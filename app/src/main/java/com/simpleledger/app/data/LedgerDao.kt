package com.simpleledger.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Database access. Ledger entries and interest rates have insert and read functions only:
 * there is deliberately no way to update or delete them from app code.
 */
@Dao
interface LedgerDao {
    @Insert
    suspend fun insertPerson(person: PersonEntity)

    @Query("UPDATE person SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query("SELECT * FROM person ORDER BY name COLLATE NOCASE")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE id = :id")
    fun observePerson(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM person")
    suspend fun allPeople(): List<PersonEntity>

    @Query("SELECT COUNT(*) FROM person")
    suspend fun personCount(): Int

    @Insert
    suspend fun insertEntry(entry: LedgerEntryEntity): Long

    @Query("SELECT * FROM ledger_entry ORDER BY seq DESC LIMIT 1")
    suspend fun lastEntry(): LedgerEntryEntity?

    @Query("SELECT * FROM ledger_entry ORDER BY seq")
    suspend fun allEntries(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entry ORDER BY seq")
    fun observeAllEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entry WHERE person_id = :personId ORDER BY seq")
    suspend fun entriesFor(personId: String): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entry WHERE person_id = :personId ORDER BY seq")
    fun observeEntriesFor(personId: String): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entry WHERE seq = :seq")
    suspend fun entry(seq: Long): LedgerEntryEntity?

    @Query("SELECT * FROM ledger_entry WHERE seq = :seq")
    fun observeEntry(seq: Long): Flow<LedgerEntryEntity?>

    @Query("SELECT * FROM ledger_entry WHERE reverses_seq = :seq")
    suspend fun reversalOf(seq: Long): LedgerEntryEntity?

    @Query("SELECT COUNT(*) FROM ledger_entry")
    suspend fun entryCount(): Int

    @Insert
    suspend fun insertRate(rate: InterestRateEntity): Long

    @Query("SELECT * FROM interest_rate ORDER BY seq")
    suspend fun allRates(): List<InterestRateEntity>

    @Query("SELECT * FROM interest_rate ORDER BY seq")
    fun observeAllRates(): Flow<List<InterestRateEntity>>

    @Query("SELECT * FROM interest_rate WHERE person_id = :personId ORDER BY seq")
    suspend fun ratesFor(personId: String): List<InterestRateEntity>

    @Query("SELECT * FROM interest_rate WHERE person_id = :personId ORDER BY seq")
    fun observeRatesFor(personId: String): Flow<List<InterestRateEntity>>
}
