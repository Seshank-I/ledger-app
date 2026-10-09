package com.simpleledger.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.simpleledger.app.R
import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.LedgerEntryEntity
import com.simpleledger.app.domain.Dates
import com.simpleledger.app.domain.Money
import com.simpleledger.app.ui.theme.LedgerColors
import java.time.LocalDate

// Plain-language wording shared by every screen. No "debit", "credit" or minus signs.

@Composable
fun friendlyDate(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> Dates.format(d)
    }
}

/** "today", "yesterday", "on 3 Oct 2026" — for use inside a sentence. */
@Composable
fun datePhrase(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> stringResource(R.string.today_lower)
        today.minusDays(1) -> stringResource(R.string.yesterday_lower)
        else -> stringResource(R.string.on_date, Dates.format(d))
    }
}

@Composable
fun balanceSentence(name: String, balancePaise: Long): String = when {
    balancePaise > 0 -> stringResource(R.string.balance_they_owe, name, Money.format(balancePaise))
    balancePaise < 0 -> stringResource(R.string.balance_you_owe, name, Money.format(balancePaise))
    else -> stringResource(R.string.balance_settled)
}

@Composable
fun balanceColor(balancePaise: Long): Color = when {
    balancePaise > 0 -> LedgerColors.gotText
    balancePaise < 0 -> LedgerColors.gaveText
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun directionColor(direction: Direction): Color =
    if (direction == Direction.GAVE) LedgerColors.gaveText else LedgerColors.gotText

/** "You gave ₹500", "You got ₹500", "Interest added: ₹600", "Cancelling entry". */
@Composable
fun entryHeadline(e: LedgerEntryEntity): String {
    val amount = Money.format(e.amountPaise)
    return when (e.kind) {
        EntryKind.NORMAL ->
            if (e.direction == Direction.GAVE) stringResource(R.string.entry_gave, amount) else stringResource(R.string.entry_got, amount)
        EntryKind.INTEREST ->
            if (e.direction == Direction.GAVE) stringResource(R.string.entry_interest, amount) else stringResource(R.string.entry_interest_owed, amount)
        EntryKind.REVERSAL -> stringResource(R.string.entry_reversal)
    }
}

@Composable
fun interestPeriod(e: LedgerEntryEntity): String? {
    val from = e.interestFrom ?: return null
    val to = e.interestTo ?: return null
    return stringResource(R.string.interest_period, Dates.formatIso(from), Dates.formatIso(to))
}
