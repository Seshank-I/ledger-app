package com.simpleledger.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpleledger.app.AppContainer
import com.simpleledger.app.R
import com.simpleledger.app.data.PersonEntity
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.InterestCalculator
import com.simpleledger.app.domain.InterestPosting
import com.simpleledger.app.domain.Money
import com.simpleledger.app.domain.Rates
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.BigTextField
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.LedgerDatePicker
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.components.balanceSentence
import com.simpleledger.app.ui.components.friendlyDate
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SetInterestViewModel(private val c: AppContainer, private val personId: String) : ViewModel() {
    val person = c.repo.observePerson(personId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var rateText by mutableStateOf("")
    var from by mutableStateOf(LocalDate.now())
    var currentBp by mutableStateOf(0)
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val entries = c.repo.entriesFor(personId)
            val rates = c.repo.ratesFor(personId)
            currentBp = InterestCalculator.state(entries, rates, LocalDate.now()).currentRateBp
            if (currentBp > 0) rateText = Rates.plain(currentBp)
            // A first rate usually applies from the first loan; a change applies from today.
            if (rates.isEmpty()) {
                entries.minOfOrNull { LocalDate.parse(it.occurredOn) }?.let { from = it }
            }
        }
    }

    fun save(bp: Int, onDone: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            c.repo.setRate(personId, bp, from)
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetInterestScreen(personId: String, onBack: () -> Unit) {
    val c = appContainer()
    val vm: SetInterestViewModel = viewModel(key = "rate-$personId") { SetInterestViewModel(c, personId) }
    val person by vm.person.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val parsed = Rates.parse(vm.rateText)

    ScreenScaffold(title = stringResource(R.string.set_interest_title, person?.name.orEmpty()), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.rate_prompt), style = MaterialTheme.typography.titleLarge)
            BigTextField(vm.rateText, { vm.rateText = it; showError = false }, stringResource(R.string.rate_label), KeyboardType.Decimal)
            Text(stringResource(R.string.rate_example), style = MaterialTheme.typography.bodyLarge, color = LedgerColors.muted)
            if (parsed != null) {
                Text(stringResource(R.string.rate_yearly, Rates.yearlyPercent(parsed)), style = MaterialTheme.typography.titleMedium)
            }
            if (showError) {
                Text(stringResource(R.string.rate_invalid), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.start_date, friendlyDate(vm.from)), style = MaterialTheme.typography.titleMedium)
            BigOutlinedButton(stringResource(R.string.change_date), { showPicker = true })
            Text(stringResource(R.string.interest_note), style = MaterialTheme.typography.bodyLarge)
            BigButton(stringResource(R.string.save), {
                if (parsed == null) showError = true else vm.save(parsed, onBack)
            }, enabled = !vm.saving)
            if (vm.currentBp > 0) {
                BigOutlinedButton(stringResource(R.string.stop_interest, friendlyDate(vm.from)), { vm.save(0, onBack) })
            }
        }
    }

    if (showPicker) {
        LedgerDatePicker(initial = vm.from, onPicked = { vm.from = it }, onDismiss = { showPicker = false })
    }
}

class AddInterestViewModel(private val c: AppContainer, private val personId: String) : ViewModel() {
    data class Ui(val loaded: Boolean = false, val person: PersonEntity? = null, val posting: InterestPosting? = null, val balance: Long = 0)

    var ui by mutableStateOf(Ui())
        private set
    private var saving = false

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val entries = c.repo.entriesFor(personId)
            val state = InterestCalculator.state(entries, c.repo.ratesFor(personId), today)
            ui = Ui(
                loaded = true,
                person = c.repo.observePerson(personId).first(),
                posting = InterestCalculator.postingFor(state, today),
                balance = state.balancePaise,
            )
        }
    }

    fun save(onDone: () -> Unit) {
        val p = ui.posting ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            c.repo.addInterest(personId, p)
            onDone()
        }
    }
}

@Composable
fun AddInterestScreen(personId: String, onBack: () -> Unit) {
    val c = appContainer()
    val vm: AddInterestViewModel = viewModel(key = "interest-$personId") { AddInterestViewModel(c, personId) }
    val ui = vm.ui
    val name = ui.person?.name.orEmpty()

    ScreenScaffold(title = stringResource(R.string.add_interest_title), onBack = onBack) { padding ->
        if (!ui.loaded) return@ScreenScaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val p = ui.posting
            if (p == null) {
                Text(stringResource(R.string.no_interest_due), style = MaterialTheme.typography.headlineSmall)
                BigButton(stringResource(R.string.go_back), onBack)
            } else {
                LedgerCard {
                    Text(
                        stringResource(R.string.add_interest_confirm, Money.format(p.amountSigned), Dates.format(p.from), Dates.format(p.to)),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.after_this, balanceSentence(name, ui.balance + p.amountSigned)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                BigButton(stringResource(R.string.save), { vm.save(onBack) })
                BigOutlinedButton(stringResource(R.string.go_back), onBack)
            }
        }
    }
}
