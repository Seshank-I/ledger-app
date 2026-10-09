package com.simpleledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PersonEntity::class, LedgerEntryEntity::class, InterestRateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        /**
         * Rules enforced by SQLite itself, so even buggy app code cannot change history.
         * Created once, when the database file is first made.
         */
        val TRIGGERS = listOf(
            // Ledger entries can never be changed.
            """
            CREATE TRIGGER ledger_no_update BEFORE UPDATE ON ledger_entry
            BEGIN SELECT RAISE(ABORT, 'ledger_entry is append-only'); END
            """,
            // Ledger entries can never be removed.
            """
            CREATE TRIGGER ledger_no_delete BEFORE DELETE ON ledger_entry
            BEGIN SELECT RAISE(ABORT, 'ledger_entry is append-only'); END
            """,
            // Every amount must be more than zero; direction carries the sign.
            """
            CREATE TRIGGER ledger_amount_positive BEFORE INSERT ON ledger_entry
            WHEN NEW.amount_paise <= 0
            BEGIN SELECT RAISE(ABORT, 'amount must be positive'); END
            """,
            // Only a reversal may point at another entry.
            """
            CREATE TRIGGER ledger_reversal_link BEFORE INSERT ON ledger_entry
            WHEN NEW.kind <> 'REVERSAL' AND NEW.reverses_seq IS NOT NULL
            BEGIN SELECT RAISE(ABORT, 'only a reversal may reference an entry'); END
            """,
            // A reversal must cancel an existing, non-reversal entry of the same person,
            // with the same amount in the opposite direction.
            """
            CREATE TRIGGER ledger_reversal_valid BEFORE INSERT ON ledger_entry
            WHEN NEW.kind = 'REVERSAL'
            BEGIN
              SELECT RAISE(ABORT, 'invalid reversal')
              WHERE NOT EXISTS (
                SELECT 1 FROM ledger_entry o
                WHERE o.seq = NEW.reverses_seq
                  AND o.kind IN ('NORMAL', 'INTEREST')
                  AND o.person_id = NEW.person_id
                  AND o.amount_paise = NEW.amount_paise
                  AND o.direction <> NEW.direction
              );
            END
            """,
            // Interest rates are history too: never changed or removed.
            """
            CREATE TRIGGER rate_no_update BEFORE UPDATE ON interest_rate
            BEGIN SELECT RAISE(ABORT, 'interest_rate is append-only'); END
            """,
            """
            CREATE TRIGGER rate_no_delete BEFORE DELETE ON interest_rate
            BEGIN SELECT RAISE(ABORT, 'interest_rate is append-only'); END
            """,
            // People are hidden, never deleted, so their history always has an owner.
            """
            CREATE TRIGGER person_no_delete BEFORE DELETE ON person
            BEGIN SELECT RAISE(ABORT, 'people are hidden, not deleted'); END
            """,
        ).map { it.trimIndent() }

        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                TRIGGERS.forEach(db::execSQL)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ledger.db")
                .addCallback(CALLBACK)
                .build()
    }
}
