package com.simpleledger.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.simpleledger.app.R
import com.simpleledger.app.data.AppDatabase
import com.simpleledger.app.data.AppPrefs
import com.simpleledger.app.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val repo: LedgerRepository,
    private val prefs: AppPrefs,
) {
    private val dao = db.ledgerDao()

    fun fileName(): String = "ledger-backup-${LocalDate.now()}.json"

    suspend fun backupJson(): String = withContext(Dispatchers.IO) {
        BackupFormat.toJson(
            BackupContents(
                people = dao.allPeople(),
                rates = dao.allRates(),
                entries = dao.allEntries(),
                exportedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Writes a backup into the app's cache and returns a share-sheet intent for it. */
    suspend fun shareIntent(): Intent {
        val json = backupJson()
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "backups").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, fileName()).apply { writeText(json) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.markBackup()
        return Intent.createChooser(send, context.getString(R.string.backup_share_title))
    }

    /** Saves a backup to a place the user picked (Files, Google Drive, SD card). */
    suspend fun writeTo(uri: Uri) {
        val json = backupJson()
        withContext(Dispatchers.IO) {
            val out = context.contentResolver.openOutputStream(uri) ?: throw BackupException(R.string.backup_failed)
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
        prefs.markBackup()
    }

    suspend fun read(uri: Uri): BackupContents = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupException(R.string.backup_bad_file)
        BackupFormat.parse(text)
    }

    /** Loads a checked backup into an empty app. Never mixes with or overwrites existing records. */
    suspend fun restore(contents: BackupContents) {
        db.withTransaction {
            if (!repo.isEmpty()) throw BackupException(R.string.restore_not_empty)
            contents.people.forEach { dao.insertPerson(it) }
            contents.rates.sortedBy { it.seq }.forEach { dao.insertRate(it) }
            contents.entries.sortedBy { it.seq }.forEach { dao.insertEntry(it) }
        }
        prefs.markBackup()
    }
}
