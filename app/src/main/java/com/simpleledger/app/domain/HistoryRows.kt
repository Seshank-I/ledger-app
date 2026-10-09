package com.simpleledger.app.domain

import com.simpleledger.app.data.EntryKind
import com.simpleledger.app.data.LedgerEntryEntity

/** One line of the history table, and when it was cancelled (if it was). */
data class EntryRow(
    val entry: LedgerEntryEntity,
    val cancelledAt: Long?,
    /** Running balance after this line, counting only entries that were never cancelled. */
    val balanceAfter: Long,
)

/**
 * The rows the history table shows, newest first.
 *
 * Cancelling entries stay in the database (the ledger is append-only) but are not shown as rows:
 * the entry they cancel is shown crossed out with its cancellation date instead, like a struck-through
 * line in a paper khata. The running balance ignores cancelled entries, so a mistake never makes the
 * balance jump and come back.
 */
fun historyRows(entries: List<LedgerEntryEntity>): List<EntryRow> {
    val reversalOf = entries.filter { it.reversesSeq != null }.associateBy { it.reversesSeq!! }
    var running = 0L
    return entries
        .sortedBy { it.seq }
        .filter { it.kind != EntryKind.REVERSAL }
        .map { e ->
            val cancelledAt = reversalOf[e.seq]?.recordedAt
            if (cancelledAt == null) running += InterestCalculator.signed(e)
            EntryRow(e, cancelledAt, running)
        }
        .reversed()
}
