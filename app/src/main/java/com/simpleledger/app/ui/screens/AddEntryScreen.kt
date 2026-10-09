package com.simpleledger.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpleledger.app.AppContainer
import com.simpleledger.app.R
import com.simpleledger.app.data.Direction
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.InterestCalculator
import com.simpleledger.app.domain.InterestPosting
import com.simpleledger.app.domain.Money
import com.simpleledger.app.ui.appContainer
import com.simpleledger.app.ui.components.BigButton
import com.simpleledger.app.ui.components.BigOutlinedButton
import com.simpleledger.app.ui.components.BigTextField
import com.simpleledger.app.ui.components.LedgerCard
import com.simpleledger.app.ui.components.LedgerDatePicker
import com.simpleledger.app.ui.components.NumberPad
import com.simpleledger.app.ui.components.ScreenScaffold
import com.simpleledger.app.ui.components.balanceSentence
import com.simpleledger.app.ui.components.datePhrase
import com.simpleledger.app.ui.components.directionColor
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val MAX_DIGITS = 9 // up to ₹99,99,99,999

class AddEntryViewModel(private val c: AppContainer, private val personId: String, val direction: Direction) : ViewModel() {
    enum class Step { AMOUNT, CONFIRM }

    /** What the confirm screen shows: any interest added first, and the balance afterwards. */
    data class Preview(val interestFirst: InterestPosting?, val balanceAfter: Long)

    val person = c.repo.observePerson(personId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var digits by mutableStateOf("")
        private set
    var note by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
        private set
    var step by mutableStateOf(Step.AMOUNT)
    var preview by mutableStateOf<Preview?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    val amountPaise: Long get() = Money.rupeesToPaise(digits)

    fun digit(ch: Char) {
        if (digits.length < MAX_DIGITS) digits = (digits + ch).trimStart('0')
    }

    fun backspace() {
        digits = digits.dropLast(1)
    }

    fun toConfirm() {
        step = Step.CONFIRM
        refreshPreview()
    }

    fun changeDate(d: LocalDate) {
        date = d
        refreshPreview()
    }

    private fun refreshPreview() {
        preview = null
        viewModelScope.launch {
            val entries = c.repo.entriesFor(personId)
            val rates = c.repo.ratesFor(personId)
            val signed = if (direction == Direction.GAVE) amountPaise else -amountPaise
            val interest = InterestCalculator.postingBeforeEntry(entries, rates, signed, date)
            val after = entries.sumOf(InterestCalculator::signed) + (interest?.amountSigned ?: 0L) + signed
            preview = Preview(interest, after)
        }
    }

    fun save(onDone: () -> Unit) {
        val p = preview ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            c.repo.addEntry(personId, direction, amountPaise, note, date, p.interestFirst)
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEntryScreen(personId: String, direction: Direction, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = appContainer()
    val vm: AddEntryViewModel = viewModel(key = "add-$personId-$direction") { AddEntryViewModel(c, personId, direction) }
    val person by vm.person.collectAsStateWithLifecycle()
    val name = person?.name.orEmpty()
    val gave = direction == Direction.GAVE
    val buttonColor = if (gave) LedgerColors.gaveButton else LedgerColors.gotButton
    var showAmountError by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    val back = { if (vm.step == AddEntryViewModel.Step.CONFIRM) vm.step = AddEntryViewModel.Step.AMOUNT else onBack() }
    BackHandler(onBack = back)

    val title = stringResource(if (gave) R.string.add_title_gave else R.string.add_title_got, name)
    ScreenScaffold(title = title, onBack = back) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (vm.step) {
                AddEntryViewModel.Step.AMOUNT -> {
                    Text(stringResource(R.string.enter_amount), style = MaterialTheme.typography.titleLarge)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            Money.format(vm.amountPaise),
                            style = MaterialTheme.typography.displaySmall,
                            color = directionColor(direction),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    NumberPad(onDigit = { vm.digit(it); showAmountError = false }, onBackspace = vm::backspace, showDoubleZero = true)
                    if (showAmountError) {
                        Text(stringResource(R.string.amount_required), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                    BigButton(stringResource(R.string.next), {
                        if (vm.amountPaise <= 0) showAmountError = true else vm.toConfirm()
                    }, containerColor = buttonColor, contentColor = Color.White)
                }

                AddEntryViewModel.Step.CONFIRM -> {
                    val amount = Money.format(vm.amountPaise)
                    val sentence = if (gave) {
                        stringResource(R.string.confirm_gave, name, amount, datePhrase(vm.date))
                    } else {
                        stringResource(R.string.confirm_got, amount, name, datePhrase(vm.date))
                    }
                    LedgerCard {
                        Text(sentence, style = MaterialTheme.typography.headlineSmall)
                        vm.preview?.interestFirst?.let { p ->
                            Text(
                                stringResource(R.string.confirm_interest_first, Money.format(p.amountSigned), Dates.format(p.from), Dates.format(p.to)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        vm.preview?.let { p ->
                            Text(
                                stringResource(R.string.after_this, balanceSentence(name, p.balanceAfter)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (showNote || vm.note.isNotEmpty()) {
                        BigTextField(vm.note, { vm.note = it }, stringResource(R.string.note_label), multiLine = true)
                    } else {
                        BigOutlinedButton(stringResource(R.string.add_note), { showNote = true })
                    }
                    BigOutlinedButton(stringResource(R.string.change_date), { showPicker = true }, icon = Icons.Filled.DateRange)
                    BigButton(
                        stringResource(R.string.save),
                        { vm.save(onSaved) },
                        enabled = vm.preview != null && !vm.saving,
                        containerColor = buttonColor,
                        contentColor = Color.White,
                    )
                    BigOutlinedButton(stringResource(R.string.go_back), { vm.step = AddEntryViewModel.Step.AMOUNT })
                }
            }
        }
    }

    if (showPicker) {
        LedgerDatePicker(initial = vm.date, onPicked = vm::changeDate, onDismiss = { showPicker = false })
    }
}
