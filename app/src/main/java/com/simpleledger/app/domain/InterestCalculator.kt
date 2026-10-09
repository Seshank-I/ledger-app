package com.simpleledger.app.domain

import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.InterestRateEntity
import com.simpleledger.app.data.LedgerEntryEntity
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sign

/**
 * Where an account stands. All amounts are signed paise from your point of view:
 * positive = they owe you, negative = you owe them.
 */
data class AccountState(
    /** Sum of every ledger entry. This is the official balance. */
    val balancePaise: Long = 0,
    val principalPaise: Long = 0,
    /** Interest already added to the ledger and not yet paid off. */
    val unpaidInterestPaise: Long = 0,
    /** Interest built up since it was last added; not in the ledger yet. */
    val accruedPaise: Long = 0,
    val accrualFrom: LocalDate? = null,
    val currentRateBp: Int = 0,
    val hasInterest: Boolean = false,
)

/** Interest the user is about to add for the days [from] (inclusive) to [to] (exclusive). */
data class InterestPosting(val from: LocalDate, val to: LocalDate, val amountSigned: Long, val rateBp: Int)

/**
 * Simple interest, counted per day: principal × rate ÷ 100 ÷ 30.
 * Rates are in hundredths of a percent per month, so ₹2 per ₹100 per month = 200.
 */
object InterestCalculator {
    private val DIVISOR = BigDecimal(10_000L * 30L)

    fun signed(e: LedgerEntryEntity): Long = if (e.direction == Direction.GAVE) e.amountPaise else -e.amountPaise

    fun state(entries: List<LedgerEntryEntity>, rates: List<InterestRateEntity>, asOf: LocalDate): AccountState {
        val balance = entries.sumOf(::signed)

        // A cancelled entry and its cancelling entry are both left out of the interest maths.
        val reversed = entries.mapNotNullTo(HashSet()) { it.reversesSeq }
        val effective = entries
            .filter { it.kind != EntryKind.REVERSAL && it.seq !in reversed }
            .sortedWith(compareBy<LedgerEntryEntity>({ it.occurredOn }, { it.seq }))

        // Replay in date order. A payment that reduces the debt clears unpaid interest first.
        var principal = 0L
        var unpaid = 0L
        val principalChanges = ArrayList<Pair<LocalDate, Long>>()
        for (e in effective) {
            val s = signed(e)
            if (e.kind == EntryKind.INTEREST) {
                unpaid += s
            } else if (unpaid != 0L && s.sign == -unpaid.sign) {
                val toInterest = minOf(abs(s), abs(unpaid)) * s.sign
                unpaid += toInterest
                principal += s - toInterest
            } else {
                principal += s
            }
            principalChanges += LocalDate.parse(e.occurredOn) to principal
        }

        val sortedRates = rates.sortedWith(compareBy<InterestRateEntity>({ it.effectiveFrom }, { it.seq }))
        val currentRate = sortedRates.lastOrNull { it.effectiveFrom <= asOf.toString() }?.rateBp ?: 0
        val hasInterest = sortedRates.isNotEmpty() || effective.any { it.kind == EntryKind.INTEREST }

        // Interest builds up from the end of the last interest added, or from the first entry.
        val lastAddedTo = effective.filter { it.kind == EntryKind.INTEREST }.mapNotNull { it.interestTo }.maxOrNull()
        val start = lastAddedTo?.let(LocalDate::parse) ?: principalChanges.firstOrNull()?.first

        var accrued = 0L
        var accrualFrom: LocalDate? = null
        if (start != null && sortedRates.isNotEmpty()) {
            var total = BigInteger.ZERO
            var day: LocalDate = start
            var ci = 0
            var ri = 0
            var p = 0L
            var bp = 0
            while (day.isBefore(asOf)) {
                while (ci < principalChanges.size && !principalChanges[ci].first.isAfter(day)) {
                    p = principalChanges[ci].second
                    ci++
                }
                while (ri < sortedRates.size && !LocalDate.parse(sortedRates[ri].effectiveFrom).isAfter(day)) {
                    bp = sortedRates[ri].rateBp
                    ri++
                }
                if (p != 0L && bp != 0) {
                    if (accrualFrom == null) accrualFrom = day
                    total += BigInteger.valueOf(p) * BigInteger.valueOf(bp.toLong())
                }
                day = day.plusDays(1)
            }
            accrued = BigDecimal(total).divide(DIVISOR, 0, RoundingMode.HALF_UP).longValueExact()
        }
        if (accrued == 0L) accrualFrom = null

        return AccountState(
            balancePaise = balance,
            principalPaise = principal,
            unpaidInterestPaise = unpaid,
            accruedPaise = accrued,
            accrualFrom = accrualFrom,
            currentRateBp = currentRate,
            hasInterest = hasInterest,
        )
    }

    /** The interest to add now, or null when nothing is due. */
    fun postingFor(state: AccountState, asOf: LocalDate): InterestPosting? {
        val from = state.accrualFrom ?: return null
        if (state.accruedPaise == 0L || !from.isBefore(asOf)) return null
        return InterestPosting(from = from, to = asOf, amountSigned = state.accruedPaise, rateBp = state.currentRateBp)
    }

    /**
     * Interest to add before recording a new entry of [signedAmount] on [date].
     * Only a payment that brings the debt down triggers it, so it pays interest before principal.
     */
    fun postingBeforeEntry(
        entries: List<LedgerEntryEntity>,
        rates: List<InterestRateEntity>,
        signedAmount: Long,
        date: LocalDate,
    ): InterestPosting? {
        if (rates.isEmpty()) return null
        val s = state(entries, rates, date)
        val owed = s.principalPaise + s.unpaidInterestPaise + s.accruedPaise
        if (owed == 0L || signedAmount.sign == owed.sign) return null
        return postingFor(s, date)
    }
}
