package com.simpleledger.app.domain

import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.InterestRateEntity
import com.simpleledger.app.data.LedgerEntryEntity

/** Factory functions for test fixtures. Amounts are in rupees for readability. */
object TestEntries {
    private var nextSeq = 1L

    fun entry(
        direction: Direction,
        rupees: Long,
        on: String,
        kind: EntryKind = EntryKind.NORMAL,
        reverses: Long? = null,
        interestFrom: String? = null,
        interestTo: String? = null,
        seq: Long = nextSeq++,
        personId: String = "p1",
    ) = LedgerEntryEntity(
        seq = seq,
        id = "e$seq",
        personId = personId,
        direction = direction,
        amountPaise = rupees * 100,
        note = null,
        occurredOn = on,
        recordedAt = seq,
        kind = kind,
        reversesSeq = reverses,
        interestFrom = interestFrom,
        interestTo = interestTo,
        rateBp = null,
        prevHash = "",
        hash = "",
    )

    fun rate(bp: Int, from: String, seq: Long = nextSeq++) =
        InterestRateEntity(seq = seq, personId = "p1", rateBp = bp, effectiveFrom = from, recordedAt = seq)

    /** Links entries into a valid hash chain, in seq order. */
    fun chained(entries: List<LedgerEntryEntity>): List<LedgerEntryEntity> {
        var prev = HashChain.GENESIS
        return entries.sortedBy { it.seq }.map { e ->
            val linked = e.copy(prevHash = prev)
            linked.copy(hash = HashChain.hashOf(linked)).also { prev = it.hash }
        }
    }
}
