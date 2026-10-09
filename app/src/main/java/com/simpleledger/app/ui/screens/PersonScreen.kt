package com.simpleledger.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpleledger.app.AppContainer
import com.simpleledger.app.R
import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.PersonEntity
import com.simpleledger.app.domain.AccountState
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.EntryRow
import com.simpleledger.app.domain.historyRows
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
import com.simpleledger.app.ui.components.interestPeriod
import com.simpleledger.app.ui.theme.LedgerColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
        PersonUi(
            loaded = true,
            person = person,
            state = InterestCalculator.state(entries, rates, LocalDate.now()),
            rows = historyRows(entries),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUi())

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { c.repo.setHidden(personId, hidden) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    // One scroll position shared by the header and every row, so all columns move together.
    val tableScroll = rememberScrollState()
    val columns = historyColumns(ui.rows)
    val dateWidth = fitWidth(
        ui.rows.flatMap { Dates.tableDate(it.entry.occurredOn).toList() } + stringResource(R.string.col_date),
        min = 64.dp,
        style = MaterialTheme.typography.bodyMedium,
    )

    ScreenScaffold(title = person?.name.orEmpty(), onBack = onBack) { padding ->
        if (person == null) return@ScreenScaffold
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                LedgerCard(Modifier.padding(bottom = GAP)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = GAP)) {
                    BigButton(
                        stringResource(R.string.i_gave),
                        { onAdd(Direction.GAVE) },
                        modifier = Modifier.weight(1f),
                        containerColor = LedgerColors.gaveButton,
                        contentColor = Color.White,
                    )
                    BigButton(
                        stringResource(R.string.i_got),
                        { onAdd(Direction.GOT) },
                        modifier = Modifier.weight(1f),
                        containerColor = LedgerColors.gotButton,
                        contentColor = Color.White,
                    )
                }
            }
            item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
            if (ui.rows.isEmpty()) {
                item { Text(stringResource(R.string.no_entries), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = GAP)) }
            }
            if (ui.rows.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.table_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LedgerColors.muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                stickyHeader { HistoryHeader(columns, tableScroll, dateWidth) }
                items(ui.rows, key = { it.entry.seq }) { row -> HistoryRow(row, columns, tableScroll, dateWidth) { onEntry(row.entry.seq) } }
                item { Spacer(Modifier.height(GAP)) }
            }
            item {
                BigOutlinedButton(
                    stringResource(if (s.hasInterest) R.string.change_interest else R.string.set_interest),
                    onSetInterest,
                    modifier = Modifier.padding(bottom = GAP),
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

/**
 * A history table column. The Date column is fixed on the left; these scroll sideways.
 * To add a column, add one entry to [historyColumns].
 */
private class HistoryColumn(
    @StringRes val title: Int,
    val width: Dp,
    val tint: Color? = null,
    val cell: @Composable (EntryRow) -> Unit,
)

private val GAP = 14.dp
private val CELL_PADDING = 10.dp

/** Widths grow with the phone's font size so large text is never cut off; the table just scrolls further. */
@Composable
private fun scaled(width: Dp): Dp = width * LocalDensity.current.fontScale

/**
 * Width that fits the widest of [texts] on one line, plus cell padding, but never below [min].
 * Money columns use this so an amount like ₹99,99,99,999 never breaks across lines.
 */
@Composable
private fun fitWidth(texts: List<String>, min: Dp, style: TextStyle = MaterialTheme.typography.titleMedium): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widestPx = remember(texts, style, density) {
        texts.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
    }
    return maxOf(min, with(density) { widestPx.toDp() } + CELL_PADDING * 2 + 4.dp)
}

@Composable
private fun historyColumns(rows: List<EntryRow>): List<HistoryColumn> {
    val muted = LedgerColors.muted
    val gaveTint = LedgerColors.gaveText.copy(alpha = 0.08f)
    val gotTint = LedgerColors.gotText.copy(alpha = 0.08f)
    val gaveTitle = stringResource(R.string.col_gave)
    val gotTitle = stringResource(R.string.col_got)
    val gaveWidth = fitWidth(rows.filter { it.entry.direction == Direction.GAVE }.map { Money.format(it.entry.amountPaise) } + gaveTitle, scaled(110.dp))
    val gotWidth = fitWidth(rows.filter { it.entry.direction == Direction.GOT }.map { Money.format(it.entry.amountPaise) } + gotTitle, scaled(110.dp))
    val balanceWidth = fitWidth(rows.map { Money.format(it.balanceAfter) }, scaled(120.dp))
    return listOf(
        HistoryColumn(R.string.col_gave, gaveWidth, gaveTint) { row ->
            if (row.entry.direction == Direction.GAVE) AmountCell(row)
        },
        HistoryColumn(R.string.col_got, gotWidth, gotTint) { row ->
            if (row.entry.direction == Direction.GOT) AmountCell(row)
        },
        HistoryColumn(R.string.col_balance, balanceWidth) { row ->
            val b = row.balanceAfter
            Column {
                Text(Money.format(b), style = MaterialTheme.typography.titleMedium, color = balanceColor(b), softWrap = false)
                Text(
                    stringResource(
                        when {
                            b > 0 -> R.string.bal_they_owe
                            b < 0 -> R.string.bal_you_owe
                            else -> R.string.bal_settled
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        },
        HistoryColumn(R.string.col_note, scaled(200.dp)) { row ->
            row.entry.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        },
        HistoryColumn(R.string.col_type, scaled(190.dp)) { row ->
            val e = row.entry
            Column {
                val type = when (e.kind) {
                    EntryKind.NORMAL -> stringResource(if (e.direction == Direction.GAVE) R.string.type_gave else R.string.type_got)
                    EntryKind.INTEREST -> stringResource(R.string.type_interest)
                    // Not shown as rows (see historyRows), kept so the list stays complete.
                    EntryKind.REVERSAL -> stringResource(R.string.entry_reversal)
                }
                Text(type, style = MaterialTheme.typography.bodyMedium)
                interestPeriod(e)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
        },
        HistoryColumn(R.string.col_status, scaled(170.dp)) { row ->
            row.cancelledAt?.let {
                Text(
                    stringResource(R.string.entry_cancelled_on, Dates.format(Dates.dateOfMillis(it))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        HistoryColumn(R.string.col_saved, scaled(190.dp)) { row ->
            Text(Dates.formatMillis(row.entry.recordedAt), style = MaterialTheme.typography.bodySmall, color = muted)
        },
    )
}

/** Amount in the "You gave" or "You got" column. Crossed out and grey when cancelled; grey for a cancelling entry. */
@Composable
private fun AmountCell(row: EntryRow) {
    val e = row.entry
    val cancelled = row.cancelledAt != null
    val quiet = cancelled || e.kind == EntryKind.REVERSAL
    Text(
        Money.format(e.amountPaise),
        style = MaterialTheme.typography.titleMedium,
        color = if (quiet) LedgerColors.muted else directionColor(e.direction),
        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        softWrap = false,
    )
}

@Composable
private fun HistoryHeader(columns: List<HistoryColumn>, scroll: ScrollState, dateWidth: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        HeaderCell(stringResource(R.string.col_date), dateWidth)
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scroll),
        ) {
            columns.forEach { HeaderCell(stringResource(it.title), it.width) }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(CELL_PADDING),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HistoryRow(row: EntryRow, columns: List<HistoryColumn>, scroll: ScrollState, dateWidth: Dp, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(onClick = onClick),
        ) {
            // Fixed Date column: always visible so the reader knows which row they are on.
            Box(
                modifier = Modifier
                    .width(dateWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(CELL_PADDING),
            ) {
                val (dayMonth, year) = Dates.tableDate(row.entry.occurredOn)
                Column {
                    Text(dayMonth, style = MaterialTheme.typography.bodyMedium, softWrap = false)
                    Text(year, style = MaterialTheme.typography.bodySmall, color = LedgerColors.muted, softWrap = false)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(scroll),
            ) {
                columns.forEach { c ->
                    Box(
                        modifier = Modifier
                            .width(c.width)
                            .fillMaxHeight()
                            .background(c.tint ?: Color.Transparent)
                            .padding(CELL_PADDING),
                    ) {
                        c.cell(row)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
}
