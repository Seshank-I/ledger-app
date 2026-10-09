package com.simpleledger.app.domain

import com.simpleledger.app.data.Direction.GAVE
import com.simpleledger.app.data.Direction.GOT
import com.simpleledger.app.domain.TestEntries.chained
import com.simpleledger.app.domain.TestEntries.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashChainTest {
    private val chain = chained(
        listOf(entry(GAVE, 500, "2026-10-01", seq = 1), entry(GOT, 200, "2026-10-02", seq = 2), entry(GAVE, 50, "2026-10-03", seq = 3)),
    )

    @Test
    fun `intact chain verifies`() {
        assertNull(HashChain.firstBroken(chain))
    }

    @Test
    fun `changing an old amount is detected`() {
        val tampered = chain.map { if (it.seq == 2L) it.copy(amountPaise = 2_000) else it }
        assertEquals(2L, HashChain.firstBroken(tampered))
    }

    @Test
    fun `removing an entry is detected`() {
        assertEquals(3L, HashChain.firstBroken(chain.filterNot { it.seq == 2L }))
    }

    @Test
    fun `hash is 64 lowercase hex characters`() {
        val h = chain.first().hash
        assertEquals(64, h.length)
        assertEquals(h, h.lowercase())
    }
}
