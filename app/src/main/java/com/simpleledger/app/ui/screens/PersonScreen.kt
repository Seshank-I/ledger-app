package com.simpleledger.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.simpleledger.app.domain.AccountState
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.InterestCalculator
import com.simpleledger.app.domain.Money
import com.simpleledger.app.domain.Rates
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.components.balanceColor
import com.simpleledger.app.ui.components.balanceSentence
import com.simpleledger.app.ui.components.directionColor
import com.simpleledger.app.ui.components.entryHeadline
import com.simpleledger.app.ui.components.friendlyDate
import com.simpleledger.app.ui.components.interestPeriod
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One history line, with what cancelled it (if anything) and what it cancels (if it is a reversal). */
data class EntryRow(val entry: LedgerEntryEntity, val cancelledAt: Long?, val cancels: LedgerEntryEntity?)

data class PersonUi(
    val loaded: Boolean = false,
    val person: PersonEntity? = null,
    val state: AccountState = AccountState(),
    val rows: List<EntryRow> = emptyList(),
)

class PersonViewModel(private val c: AppContainer, private val personId: String) : ViewModel() {
    val ui = combine(
        c.repo.observePerson(personId),
        c.repo.observeEntriesFor(personId),
        c.repo.observeRatesFor(personId),
    ) { person, entries, rates ->
        val bySeq = entries.associateBy { it.seq }
        val reversalOf = entries.filter { it.reversesSeq != null }.associateBy { it.reversesSeq!! }
        PersonUi(
            loaded = true,
            person = person,
            state = InterestCalculator.state(entries, rates, LocalDate.now()),
            rows = entries.sortedByDescending { it.seq }.map { e ->
                EntryRow(e, reversalOf[e.seq]?.recordedAt, e.reversesSeq?.let(bySeq::get))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUi())

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { c.repo.setHidden(personId, hidden) }
    }
}

@Composable
fun PersonScreen(
    personId: String,
    onBack: () -> Unit,
    onAdd: (Direction) -> Unit,
    onEntry: (Long) -> Unit,
    onSetInterest: () -> Unit,
    onAddInterest: () -> Unit,
) {
    val c = appContainer()
    val vm: PersonViewModel = viewModel(key = "person-$personId") { PersonViewModel(c, personId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val person = ui.person
    val s = ui.state

    ScreenScaffold(title = person?.name.orEmpty(), onBack = onBack) { padding ->
        if (person == null) return@ScreenScaffold
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LedgerCard {
                    Text(
                        balanceSentence(person.name, s.balancePaise),
                        style = MaterialTheme.typography.headlineMedium,
                        color = balanceColor(s.balancePaise),
                    )
                    if (s.hasInterest) {
                        if (s.currentRateBp > 0) {
                            Text(
                                stringResource(R.string.interest_rate_line, Rates.plain(s.currentRateBp), Rates.yearlyPercent(s.currentRateBp)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            Text(stringResource(R.string.interest_stopped), style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(stringResource(R.string.principal_line, Money.format(s.principalPaise)), style = MaterialTheme.typography.bodyLarge)
                        if (s.unpaidInterestPaise != 0L) {
                            Text(
                                stringResource(R.string.unpaid_interest_line, Money.format(s.unpaidInterestPaise)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (s.accruedPaise != 0L) {
                            Text(
                                stringResource(R.string.accrued_line, Money.format(s.accruedPaise)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            BigOutlinedButton(stringResource(R.string.add_interest), onAddInterest)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(
                        stringResource(R.string.i_gave),
                        { onAdd(Direction.GAVE) },
                        modifier = Modifier.weight(1f),
                        containerColor = LedgerColors.gaveButton,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    )
                    BigButton(
                        stringResource(R.string.i_got),
                        { onAdd(Direction.GOT) },
                        modifier = Modifier.weight(1f),
                        containerColor = LedgerColors.gotButton,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge) }
            if (ui.rows.isEmpty()) {
                item { Text(stringResource(R.string.no_entries), style = MaterialTheme.typography.bodyLarge) }
            }
            items(ui.rows, key = { it.entry.seq }) { row -> EntryCard(row) { onEntry(row.entry.seq) } }
            item {
                BigOutlinedButton(
                    stringResource(if (s.hasInterest) R.string.change_interest else R.string.set_interest),
                    onSetInterest,
                )
            }
            item {
                BigOutlinedButton(
                    stringResource(if (person.hidden) R.string.show_person else R.string.hide_person),
                    { vm.setHidden(!person.hidden) },
                )
            }
        }
    }
}

@Composable
private fun EntryCard(row: EntryRow, onClick: () -> Unit) {
    val e = row.entry
    val cancelled = row.cancelledAt != null
    val isReversal = e.kind == EntryKind.REVERSAL
    val muted = LedgerColors.muted
    val arrow = when {
        isReversal -> "↺"
        e.direction == Direction.GAVE -> "↑"
        else -> "↓"
    }
    val arrowColor = if (cancelled || isReversal) muted else directionColor(e.direction)

    LedgerCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Text(arrow, fontSize = 34.sp, color = arrowColor, modifier = Modifier.width(44.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    entryHeadline(e),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (cancelled || isReversal) muted else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                )
                Text(friendlyDate(LocalDate.parse(e.occurredOn)), style = MaterialTheme.typography.bodyMedium, color = muted)
                e.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                interestPeriod(e)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
                row.cancels?.let { original ->
                    Text(
                        stringResource(R.string.entry_reversal_detail, entryHeadline(original), Dates.formatIso(original.occurredOn)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                }
                row.cancelledAt?.let {
                    Text(
                        stringResource(R.string.entry_cancelled_on, Dates.format(Dates.dateOfMillis(it))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
