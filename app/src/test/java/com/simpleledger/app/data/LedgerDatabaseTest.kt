package com.simpleledger.app.data

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simpleledger.app.backup.BackupContents
import com.simpleledger.app.backup.BackupException
import com.simpleledger.app.backup.BackupFormat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private val sql get() = db.openHelper.writableDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(AppDatabase.CALLBACK)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun personWithEntry(): Pair<String, Long> = runBlocking {
        val id = repo.addPerson("Ramesh", null)
        id to repo.addEntry(id, Direction.GAVE, 50_000, null, LocalDate.parse("2026-10-01"))
    }

    @Test
    fun `database rejects changing an entry`() {
        personWithEntry()
        assertThrows(SQLiteException::class.java) { sql.execSQL("UPDATE ledger_entry SET amount_paise = 1") }
    }

    @Test
    fun `database rejects deleting an entry`() {
        personWithEntry()
        assertThrows(SQLiteException::class.java) { sql.execSQL("DELETE FROM ledger_entry") }
    }

    @Test
    fun `database rejects deleting a person`() {
        personWithEntry()
        assertThrows(SQLiteException::class.java) { sql.execSQL("DELETE FROM person") }
    }

    @Test
    fun `database rejects a reversal that does not mirror the original`() {
        val (personId, seq) = personWithEntry()
        assertThrows(SQLiteException::class.java) {
            sql.execSQL(
                """INSERT INTO ledger_entry (id, person_id, direction, amount_paise, occurred_on, recorded_at, kind, reverses_seq, prev_hash, hash)
                   VALUES ('x', '$personId', 'GOT', 1, '2026-10-02', 0, 'REVERSAL', $seq, '', '')""",
            )
        }
    }

    @Test
    fun `cancelling keeps the original and balances to zero`() = runBlocking<Unit> {
        val (personId, seq) = personWithEntry()
        repo.reverse(seq)
        val entries = repo.entriesFor(personId)
        assertEquals(2, entries.size)
        assertEquals(50_000L, entries.first().amountPaise)
        assertEquals(0L, entries.sumOf { if (it.direction == Direction.GAVE) it.amountPaise else -it.amountPaise })
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.reverse(seq) } }
        assertNull(repo.verifyChain())
    }

    @Test
    fun `backup round trip restores identical records`() = runBlocking<Unit> {
        val (personId, seq) = personWithEntry()
        repo.reverse(seq)
        repo.setRate(personId, 200, LocalDate.parse("2026-10-01"))
        val dao = db.ledgerDao()
        val json = BackupFormat.toJson(BackupContents(dao.allPeople(), dao.allRates(), dao.allEntries(), 0))

        val parsed = BackupFormat.parse(json)
        assertEquals(dao.allEntries(), parsed.entries)
        assertEquals(dao.allRates(), parsed.rates)

        val tampered = json.replace("\"amountPaise\": 50000", "\"amountPaise\": 5000")
        assertThrows(BackupException::class.java) { BackupFormat.parse(tampered) }
    }
}
