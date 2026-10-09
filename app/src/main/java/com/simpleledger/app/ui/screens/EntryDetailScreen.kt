package com.simpleledger.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpleledger.app.AppContainer
import com.simpleledger.app.R
import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.LedgerEntryEntity
import com.simpleledger.app.data.PersonEntity
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.InterestCalculator
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.components.balanceSentence
import com.simpleledger.app.ui.components.entryHeadline
import com.simpleledger.app.ui.components.friendlyDate
import com.simpleledger.app.ui.components.interestPeriod
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EntryDetailUi(
    val loaded: Boolean = false,
    val entry: LedgerEntryEntity? = null,
    val person: PersonEntity? = null,
    val reversal: LedgerEntryEntity? = null,
    val cancels: LedgerEntryEntity? = null,
    val balanceNow: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailViewModel(private val c: AppContainer, private val seq: Long) : ViewModel() {
    enum class Step { VIEW, CONFIRM, DONE }

    var step by mutableStateOf(Step.VIEW)
    private var working = false

    val ui = c.repo.observeEntry(seq).flatMapLatest { e ->
        if (e == null) {
            flowOf(EntryDetailUi(loaded = true))
        } else {
            combine(c.repo.observePerson(e.personId), c.repo.observeEntriesFor(e.personId)) { p, entries ->
                EntryDetailUi(
                    loaded = true,
                    entry = e,
                    person = p,
                    reversal = entries.firstOrNull { it.reversesSeq == e.seq },
                    cancels = e.reversesSeq?.let { s -> entries.firstOrNull { it.seq == s } },
                    balanceNow = entries.sumOf(InterestCalculator::signed),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailUi())

    fun cancelEntry() {
        if (working) return
        working = true
        viewModelScope.launch {
            c.repo.reverse(seq)
            step = Step.DONE
        }
    }
}

@Composable
fun EntryDetailScreen(seq: Long, onBack: () -> Unit, onEnterCorrect: (String, Direction) -> Unit) {
    val c = appContainer()
    val vm: EntryDetailViewModel = viewModel(key = "entry-$seq") { EntryDetailViewModel(c, seq) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val e = ui.entry
    val name = ui.person?.name.orEmpty()

    ScreenScaffold(title = stringResource(R.string.entry_title), onBack = onBack) { padding ->
        if (e == null) return@ScreenScaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (vm.step) {
                EntryDetailViewModel.Step.VIEW -> {
                    LedgerCard {
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        Text(entryHeadline(e), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.happened_on, friendlyDate(LocalDate.parse(e.occurredOn))), style = MaterialTheme.typography.bodyLarge)
                        e.note?.let { Text(stringResource(R.string.note_value, it), style = MaterialTheme.typography.bodyLarge) }
                        interestPeriod(e)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        ui.cancels?.let { original ->
                            Text(
                                stringResource(R.string.entry_reversal_detail, entryHeadline(original), Dates.formatIso(original.occurredOn)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            stringResource(R.string.recorded_at, Dates.formatMillis(e.recordedAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LedgerColors.muted,
                        )
                    }
                    when {
                        ui.reversal != null -> Text(
                            stringResource(R.string.already_cancelled, Dates.formatMillis(ui.reversal!!.recordedAt)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        e.kind == EntryKind.REVERSAL -> Text(
                            stringResource(R.string.cannot_cancel_reversal),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        else -> BigButton(stringResource(R.string.this_entry_wrong), { vm.step = EntryDetailViewModel.Step.CONFIRM })
                    }
                    BigOutlinedButton(stringResource(R.string.go_back), onBack)
                }

                EntryDetailViewModel.Step.CONFIRM -> {
                    LedgerCard {
                        Text(entryHeadline(e), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.cancel_explain), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.balance_now, balanceSentence(name, ui.balanceNow)), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.balance_after, balanceSentence(name, ui.balanceNow - InterestCalculator.signed(e))),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    BigButton(stringResource(R.string.yes_cancel), vm::cancelEntry)
                    BigOutlinedButton(stringResource(R.string.go_back), { vm.step = EntryDetailViewModel.Step.VIEW })
                }

                EntryDetailViewModel.Step.DONE -> {
                    Text(stringResource(R.string.cancelled_done), style = MaterialTheme.typography.headlineSmall)
                    if (e.kind == EntryKind.NORMAL) {
                        BigButton(stringResource(R.string.enter_correct_now), { onEnterCorrect(e.personId, e.direction) })
                        BigOutlinedButton(stringResource(R.string.no_done), onBack)
                    } else {
                        BigButton(stringResource(R.string.no_done), onBack)
                    }
                }
            }
        }
    }
}
