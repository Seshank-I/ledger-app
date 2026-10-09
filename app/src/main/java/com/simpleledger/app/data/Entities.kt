package com.simpleledger.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Direction {
    /** Money went from you to the other person. */
    GAVE,

    /** Money came from the other person to you. */
    GOT;

    fun opposite(): Direction = if (this == GAVE) GOT else GAVE
}

enum class EntryKind {
    /** An ordinary "I gave" / "I got" entry. */
    NORMAL,

    /** Cancels one earlier entry: same amount, opposite direction. */
    REVERSAL,

    /** Interest the user chose to add to the account. */
    INTEREST,
}

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val hidden: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * One line in the ledger. Rows are only ever inserted; database triggers reject UPDATE and DELETE.
 * Amounts are whole paise (₹1 = 100) so no floating point is ever involved.
 */
@Entity(
    tableName = "ledger_entry",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"]),
    ],
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["person_id"]),
        Index(value = ["reverses_seq"], unique = true),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    val direction: Direction,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val note: String?,
    /** ISO date (yyyy-MM-dd) the user says the money moved. */
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    /** When the phone saved the entry, epoch milliseconds. */
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    val kind: EntryKind,
    @ColumnInfo(name = "reverses_seq") val reversesSeq: Long?,
    @ColumnInfo(name = "interest_from") val interestFrom: String?,
    @ColumnInfo(name = "interest_to") val interestTo: String?,
    /** Interest rate used, in hundredths of a percent per month (₹2 per ₹100 per month = 200). */
    @ColumnInfo(name = "rate_bp") val rateBp: Int?,
    @ColumnInfo(name = "prev_hash") val prevHash: String,
    val hash: String,
)

/** Interest rate for a person from a given date. Insert-only, like the ledger. A rate of 0 stops interest. */
@Entity(
    tableName = "interest_rate",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"]),
    ],
    indices = [Index(value = ["person_id"])],
)
data class InterestRateEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "rate_bp") val rateBp: Int,
    @ColumnInfo(name = "effective_from") val effectiveFrom: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
)
