package com.simpleledger.app.domain

import com.simpleledger.app.data.Direction.GAVE
import com.simpleledger.app.data.Direction.GOT
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.domain.TestEntries.entry
import com.simpleledger.app.domain.TestEntries.rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InterestCalculatorTest {
    private val jan1 = LocalDate.parse("2026-01-01")
    private val apr1 = LocalDate.parse("2026-04-01")
    private val may1 = LocalDate.parse("2026-05-01")

    @Test
    fun `balance without interest is gave minus got`() {
        val entries = listOf(entry(GAVE, 500, "2026-10-01"), entry(GOT, 200, "2026-10-02"))
        val s = InterestCalculator.state(entries, emptyList(), LocalDate.parse("2026-10-09"))
        assertEquals(30_000L, s.balancePaise)
        assertEquals(0L, s.accruedPaise)
        assertFalse(s.hasInterest)
    }

    @Test
    fun `worked example from the design doc`() {
        val rates = listOf(rate(200, "2026-01-01"))
        val loan = entry(GAVE, 10_000, "2026-01-01")

        // 1 Apr: 90 days x 10,000 x 2% / 30 = 600
        val atApr1 = InterestCalculator.state(listOf(loan), rates, apr1)
        assertEquals(60_000L, atApr1.accruedPaise)
        assertEquals(jan1, atApr1.accrualFrom)

        // Recording a 3,000 repayment on 1 Apr adds the 600 interest first.
        val posting = InterestCalculator.postingBeforeEntry(listOf(loan), rates, -300_000L, apr1)
        assertNotNull(posting)
        assertEquals(60_000L, posting!!.amountSigned)
        assertEquals(jan1, posting.from)
        assertEquals(apr1, posting.to)

        val interest = entry(GAVE, 600, "2026-04-01", kind = EntryKind.INTEREST, interestFrom = "2026-01-01", interestTo = "2026-04-01")
        val payment = entry(GOT, 3_000, "2026-04-01")
        val afterPayment = InterestCalculator.state(listOf(loan, interest, payment), rates, apr1)
        assertEquals(760_000L, afterPayment.principalPaise)
        assertEquals(0L, afterPayment.unpaidInterestPaise)
        assertEquals(760_000L, afterPayment.balancePaise)
        assertEquals(0L, afterPayment.accruedPaise)

        // 1 May: 30 days x 7,600 x 2% / 30 = 152, shown but not added.
        val atMay1 = InterestCalculator.state(listOf(loan, interest, payment), rates, may1)
        assertEquals(15_200L, atMay1.accruedPaise)
        assertEquals(apr1, atMay1.accrualFrom)
        assertEquals(760_000L, atMay1.balancePaise)
    }

    @Test
    fun `more lending does not trigger interest posting`() {
        val rates = listOf(rate(200, "2026-01-01"))
        val loan = entry(GAVE, 10_000, "2026-01-01")
        assertNull(InterestCalculator.postingBeforeEntry(listOf(loan), rates, 100_000L, apr1))
    }

    @Test
    fun `cancelled loan earns no interest`() {
        val rates = listOf(rate(200, "2026-01-01"))
        val loan = entry(GAVE, 10_000, "2026-01-01")
        val cancel = entry(GOT, 10_000, "2026-01-02", kind = EntryKind.REVERSAL, reverses = loan.seq)
        val s = InterestCalculator.state(listOf(loan, cancel), rates, apr1)
        assertEquals(0L, s.balancePaise)
        assertEquals(0L, s.accruedPaise)
    }

    @Test
    fun `rate starting later only charges from its start date`() {
        val rates = listOf(rate(200, "2026-03-02"))
        val loan = entry(GAVE, 10_000, "2026-01-01")
        // 2 Mar to 1 Apr = 30 days -> 200
        val s = InterestCalculator.state(listOf(loan), rates, apr1)
        assertEquals(20_000L, s.accruedPaise)
        assertEquals(LocalDate.parse("2026-03-02"), s.accrualFrom)
    }

    @Test
    fun `stopping interest with rate zero`() {
        val rates = listOf(rate(200, "2026-01-01"), rate(0, "2026-01-31"))
        val loan = entry(GAVE, 10_000, "2026-01-01")
        // Only 30 days charged -> 200
        val s = InterestCalculator.state(listOf(loan), rates, apr1)
        assertEquals(20_000L, s.accruedPaise)
        assertEquals(0, s.currentRateBp)
        assertTrue(s.hasInterest)
    }

    @Test
    fun `interest you owe is negative`() {
        val rates = listOf(rate(100, "2026-01-01"))
        val borrowed = entry(GOT, 3_000, "2026-01-01")
        // 30 days x 3,000 x 1% / 30 = 30 owed by you
        val s = InterestCalculator.state(listOf(borrowed), rates, LocalDate.parse("2026-01-31"))
        assertEquals(-3_000L, s.accruedPaise)
    }

    @Test
    fun `rounding is half up to the paisa`() {
        val rates = listOf(rate(150, "2026-01-01"))
        val loan = entry(GAVE, 1, "2026-01-01") // ₹1 = 100 paise
        // 1 day: 100 x 150 / 300000 = 0.05 paise -> 0
        assertEquals(0L, InterestCalculator.state(listOf(loan), rates, LocalDate.parse("2026-01-02")).accruedPaise)
        // 10 days: 0.5 paise -> 1
        assertEquals(1L, InterestCalculator.state(listOf(loan), rates, LocalDate.parse("2026-01-11")).accruedPaise)
    }
}
