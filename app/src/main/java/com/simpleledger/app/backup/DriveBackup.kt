package com.simpleledger.app.backup

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.simpleledger.app.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Uploads the backup file to the user's own Google Drive, into a folder "Simple Ledger Backups".
 * Uses the narrow "drive.file" permission: the app can only see files it created itself,
 * never the rest of the user's Drive. Keeps the newest [KEEP] backups and deletes older ones.
 */
class DriveBackup(private val backup: BackupManager, private val prefs: AppPrefs) {
    sealed interface Outcome {
        data object Done : Outcome
        data object NothingNew : Outcome

        /** Google needs the user to (re)approve access; [intent] shows Google's own screen. */
        data class NeedsApproval(val intent: PendingIntent?) : Outcome
        data class Failed(val error: Exception) : Outcome
    }

    private fun request(): AuthorizationRequest {
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        prefs.driveAccount?.let { builder.setAccount(Account(it, "com.google")) }
        return builder.build()
    }

    /** Asks Google for access. Silent when the user already approved; otherwise the result carries a screen to show. */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    fun resultFromIntent(context: Context, data: android.content.Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    fun remember(result: AuthorizationResult) {
        result.toGoogleSignInAccount()?.email?.let { prefs.driveAccount = it }
        prefs.driveEnabled = true
        prefs.driveNeedsReconnect = false
    }

    suspend fun run(context: Context, force: Boolean = false): Outcome = try {
        val auth = authorize(context)
        val token = auth.accessToken
        if (auth.hasResolution() || token == null) {
            Outcome.NeedsApproval(auth.pendingIntent)
        } else {
            upload(token, force)
        }
    } catch (e: Exception) {
        Outcome.Failed(e)
    }

    private suspend fun upload(token: String, force: Boolean): Outcome = withContext(Dispatchers.IO) {
        // Fingerprint without the export time, so the same data gives the same fingerprint.
        val fingerprint = sha256(backup.backupJson(exportedAt = 0))
        if (!force && fingerprint == prefs.driveFingerprint) return@withContext Outcome.NothingNew

        val folder = folderId(token)
        val name = "ledger-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.json"
        uploadFile(token, folder, name, backup.backupJson())
        prune(token, folder)

        prefs.driveFingerprint = fingerprint
        prefs.markDriveBackup()
        Outcome.Done
    }

    private fun folderId(token: String): String {
        prefs.driveFolderId?.let { id ->
            val (code, body) = http("GET", "$FILES/$id?fields=id,trashed", token)
            if (code == 200 && !JSONObject(body).optBoolean("trashed")) return id
        }
        val q = "name='$FOLDER_NAME' and mimeType='$FOLDER_MIME' and trashed=false"
        val (code, body) = http("GET", "$FILES?q=${enc(q)}&fields=files(id)&spaces=drive", token)
        if (code != 200) throw IOException("Drive folder search failed: $code")
        val existing = JSONObject(body).getJSONArray("files")
        val id = if (existing.length() > 0) {
            existing.getJSONObject(0).getString("id")
        } else {
            val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", FOLDER_MIME).toString()
            val (c2, b2) = http("POST", "$FILES?fields=id", token, meta.toByteArray(), "application/json; charset=UTF-8")
            if (c2 !in 200..299) throw IOException("Drive folder create failed: $c2")
            JSONObject(b2).getString("id")
        }
        prefs.driveFolderId = id
        return id
    }

    private fun uploadFile(token: String, folderId: String, name: String, json: String) {
        val boundary = "ledger-${System.nanoTime()}"
        val meta = JSONObject().put("name", name).put("mimeType", "application/json")
            .put("parents", org.json.JSONArray().put(folderId)).toString()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").append(meta).append("\r\n")
            append("--$boundary\r\nContent-Type: application/json\r\n\r\n").append(json).append("\r\n")
            append("--$boundary--")
        }.toByteArray(Charsets.UTF_8)
        val (code, _) = http("POST", "$UPLOAD?uploadType=multipart&fields=id", token, body, "multipart/related; boundary=$boundary")
        if (code !in 200..299) throw IOException("Drive upload failed: $code")
    }

    private fun prune(token: String, folderId: String) {
        val q = "'$folderId' in parents and trashed=false"
        val (code, body) = http("GET", "$FILES?q=${enc(q)}&orderBy=createdTime desc&fields=files(id)&pageSize=200".replace(" ", "%20"), token)
        if (code != 200) return
        val files = JSONObject(body).getJSONArray("files")
        for (i in KEEP until files.length()) {
            http("DELETE", "$FILES/${files.getJSONObject(i).getString("id")}", token)
        }
    }

    private fun http(method: String, url: String, token: String, body: ByteArray? = null, contentType: String? = null): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        const val FOLDER_NAME = "Simple Ledger Backups"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val KEEP = 30
    }
}
