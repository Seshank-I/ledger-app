package com.simpleledger.app.data

import androidx.room.withTransaction
import com.simpleledger.app.domain.HashChain
import com.simpleledger.app.domain.InterestPosting
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/** The only place that writes to the ledger. Every write appends; nothing is overwritten. */
class LedgerRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val dao = db.ledgerDao()

    fun observePeople() = dao.observePeople()
    fun observePerson(id: String) = dao.observePerson(id)
    fun observeAllEntries() = dao.observeAllEntries()
    fun observeEntriesFor(personId: String) = dao.observeEntriesFor(personId)
    fun observeEntry(seq: Long) = dao.observeEntry(seq)
    fun observeAllRates() = dao.observeAllRates()
    fun observeRatesFor(personId: String) = dao.observeRatesFor(personId)

    suspend fun entriesFor(personId: String) = dao.entriesFor(personId)
    suspend fun ratesFor(personId: String) = dao.ratesFor(personId)
    suspend fun entryCount() = dao.entryCount()

    suspend fun isEmpty(): Boolean = dao.personCount() == 0 && dao.entryCount() == 0

    suspend fun addPerson(name: String, phone: String?): String {
        val id = UUID.randomUUID().toString()
        dao.insertPerson(
            PersonEntity(
                id = id,
                name = name.trim(),
                phone = phone?.trim()?.ifBlank { null },
                hidden = false,
                createdAt = now(),
            ),
        )
        return id
    }

    suspend fun setHidden(personId: String, hidden: Boolean) = dao.setHidden(personId, hidden)

    /**
     * Records money given or received. When [interestFirst] is set, the interest due up to that day
     * is appended first, in the same transaction, so a repayment clears interest before principal.
     */
    suspend fun addEntry(
        personId: String,
        direction: Direction,
        amountPaise: Long,
        note: String?,
        occurredOn: LocalDate,
        interestFirst: InterestPosting? = null,
    ): Long = db.withTransaction {
        require(amountPaise > 0) { "amount must be positive" }
        if (interestFirst != null) append(interestEntry(personId, interestFirst))
        append(
            draft(
                personId = personId,
                direction = direction,
                amountPaise = amountPaise,
                note = note?.trim()?.ifBlank { null },
                occurredOn = occurredOn,
                kind = EntryKind.NORMAL,
            ),
        )
    }

    suspend fun addInterest(personId: String, posting: InterestPosting): Long =
        db.withTransaction { append(interestEntry(personId, posting)) }

    /** Cancels an entry by appending its mirror image. The original row is left untouched. */
    suspend fun reverse(seq: Long): Long = db.withTransaction {
        val original = dao.entry(seq) ?: error("No entry $seq")
        check(original.kind != EntryKind.REVERSAL) { "A cancelling entry cannot be cancelled" }
        check(dao.reversalOf(seq) == null) { "Entry $seq is already cancelled" }
        append(
            draft(
                personId = original.personId,
                direction = original.direction.opposite(),
                amountPaise = original.amountPaise,
                note = null,
                occurredOn = today(),
                kind = EntryKind.REVERSAL,
                reversesSeq = seq,
            ),
        )
    }

    suspend fun setRate(personId: String, rateBp: Int, from: LocalDate): Long {
        require(rateBp >= 0) { "rate cannot be negative" }
        return dao.insertRate(
            InterestRateEntity(personId = personId, rateBp = rateBp, effectiveFrom = from.toString(), recordedAt = now()),
        )
    }

    /** Seq of the first entry whose hash does not match, or null when every record is intact. */
    suspend fun verifyChain(): Long? = HashChain.firstBroken(dao.allEntries())

    private fun interestEntry(personId: String, p: InterestPosting) = draft(
        personId = personId,
        direction = if (p.amountSigned > 0) Direction.GAVE else Direction.GOT,
        amountPaise = abs(p.amountSigned),
        note = null,
        occurredOn = p.to,
        kind = EntryKind.INTEREST,
        interestFrom = p.from.toString(),
        interestTo = p.to.toString(),
        rateBp = p.rateBp,
    )

    private fun draft(
        personId: String,
        direction: Direction,
        amountPaise: Long,
        note: String?,
        occurredOn: LocalDate,
        kind: EntryKind,
        reversesSeq: Long? = null,
        interestFrom: String? = null,
        interestTo: String? = null,
        rateBp: Int? = null,
    ) = LedgerEntryEntity(
        id = UUID.randomUUID().toString(),
        personId = personId,
        direction = direction,
        amountPaise = amountPaise,
        note = note,
        occurredOn = occurredOn.toString(),
        recordedAt = now(),
        kind = kind,
        reversesSeq = reversesSeq,
        interestFrom = interestFrom,
        interestTo = interestTo,
        rateBp = rateBp,
        prevHash = "",
        hash = "",
    )

    /** Links the entry to the previous one in the hash chain and inserts it. Call inside a transaction. */
    private suspend fun append(entry: LedgerEntryEntity): Long {
        val linked = entry.copy(prevHash = dao.lastEntry()?.hash ?: HashChain.GENESIS)
        return dao.insertEntry(linked.copy(hash = HashChain.hashOf(linked)))
    }
}
