package com.simpleledger.app.domain

import com.simpleledger.app.data.LedgerEntryEntity
import java.security.MessageDigest

/**
 * Each entry stores the SHA-256 of its own fields plus the previous entry's hash.
 * Changing any old entry (for example by editing a backup file) breaks every hash after it.
 */
object HashChain {
    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

    private val HEX = "0123456789abcdef".toCharArray()

    fun hashOf(e: LedgerEntryEntity): String {
        val fields = listOf(
            e.id,
            e.personId,
            e.direction.name,
            e.amountPaise.toString(),
            e.note.orEmpty(),
            e.occurredOn,
            e.recordedAt.toString(),
            e.kind.name,
            e.reversesSeq?.toString().orEmpty(),
            e.interestFrom.orEmpty(),
            e.interestTo.orEmpty(),
            e.rateBp?.toString().orEmpty(),
            e.prevHash,
        )
        // Length-prefixing each field means no two different entries can produce the same text.
        val canonical = fields.joinToString(separator = "") { "${it.length}:$it;" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val out = CharArray(digest.size * 2)
        digest.forEachIndexed { i, b ->
            val v = b.toInt() and 0xff
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0f]
        }
        return String(out)
    }

    /** Returns the seq of the first entry whose link or hash is wrong, or null if the chain is intact. */
    fun firstBroken(entries: List<LedgerEntryEntity>): Long? {
        var prev = GENESIS
        for (e in entries.sortedBy { it.seq }) {
            if (e.prevHash != prev || e.hash != hashOf(e)) return e.seq
            prev = e.hash
        }
        return null
    }
}
