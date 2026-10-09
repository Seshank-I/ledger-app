package com.simpleledger.app.domain

import com.simpleledger.app.data.Direction.GAVE
import com.simpleledger.app.data.Direction.GOT
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.domain.TestEntries.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryRowsTest {
    private val loan = entry(GAVE, 10_000, "2026-01-01", seq = 1)
    private val wrong = entry(GOT, 9_00_00_000, "2026-02-01", seq = 2)
    private val repaid = entry(GOT, 3_000, "2026-03-01", seq = 3)
    private val cancel = entry(GAVE, 9_00_00_000, "2026-03-05", kind = EntryKind.REVERSAL, reverses = 2, seq = 4)

    @Test
    fun `cancelling entries are not shown as rows`() {
        val rows = historyRows(listOf(loan, wrong, repaid, cancel))
        assertEquals(listOf(3L, 2L, 1L), rows.map { it.entry.seq })
    }

    @Test
    fun `cancelled entry carries its cancellation time`() {
        val rows = historyRows(listOf(loan, wrong, repaid, cancel)).associateBy { it.entry.seq }
        assertEquals(cancel.recordedAt, rows.getValue(2).cancelledAt)
        assertNull(rows.getValue(1).cancelledAt)
    }

    @Test
    fun `running balance ignores cancelled entries`() {
        val rows = historyRows(listOf(loan, wrong, repaid, cancel)).associateBy { it.entry.seq }
        assertEquals(1_000_000L, rows.getValue(1).balanceAfter)
        // The wrong ₹9 crore entry does not move the balance.
        assertEquals(1_000_000L, rows.getValue(2).balanceAfter)
        assertEquals(700_000L, rows.getValue(3).balanceAfter)
    }
}
