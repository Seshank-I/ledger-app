package com.simpleledger.app.domain

import com.simpleledger.app.data.Direction.GAVE
import com.simpleledger.app.domain.TestEntries.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test
    fun `money uses Indian grouping`() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹500", Money.format(50_000))
        assertEquals("₹1,000", Money.format(100_000))
        assertEquals("₹1,00,000", Money.format(10_000_000))
        assertEquals("₹12,34,567", Money.format(123_456_700))
        assertEquals("₹500.50", Money.format(50_050))
        assertEquals("₹500.05", Money.format(-50_005))
    }

    @Test
    fun `rates parse and print`() {
        assertEquals(200, Rates.parse("2"))
        assertEquals(150, Rates.parse("1.5"))
        assertNull(Rates.parse("0"))
        assertNull(Rates.parse("11"))
        assertNull(Rates.parse("abc"))
        assertEquals("2", Rates.plain(200))
        assertEquals("1.5", Rates.plain(150))
        assertEquals("24", Rates.yearlyPercent(200))
        assertEquals("18", Rates.yearlyPercent(150))
    }

    @Test
    fun `backup reminder after seven days of unsaved entries`() {
        val day = 24L * 60 * 60 * 1000
        val e = entry(GAVE, 100, "2026-10-01").copy(recordedAt = 10 * day)
        assertFalse(needsBackupReminder(emptyList(), 0, 100 * day))
        assertFalse(needsBackupReminder(listOf(e), 0, 16 * day))
        assertTrue(needsBackupReminder(listOf(e), 0, 18 * day))
        assertFalse(needsBackupReminder(listOf(e), 11 * day, 30 * day))
    }
}
