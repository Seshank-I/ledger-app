package com.simpleledger.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small settings that are not money records: backup date, PIN hash, fingerprint switch. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _lastBackupAt = MutableStateFlow(sp.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()

    fun markBackup(now: Long = System.currentTimeMillis()) {
        sp.edit().putLong(KEY_LAST_BACKUP, now).apply()
        _lastBackupAt.value = now
    }

    val pinHash: String? get() = sp.getString(KEY_PIN_HASH, null)
    val pinSalt: String? get() = sp.getString(KEY_PIN_SALT, null)

    fun setPin(hash: String, salt: String) {
        sp.edit().putString(KEY_PIN_HASH, hash).putString(KEY_PIN_SALT, salt).apply()
    }

    fun clearPin() {
        sp.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).putBoolean(KEY_BIOMETRIC, false).apply()
    }

    var biometricEnabled: Boolean
        get() = sp.getBoolean(KEY_BIOMETRIC, false)
        set(value) = sp.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    // --- Google Drive automatic backup ---
    private val _driveLastAt = MutableStateFlow(sp.getLong(KEY_DRIVE_LAST, 0L))
    val driveLastAt: StateFlow<Long> = _driveLastAt.asStateFlow()

    fun markDriveBackup(now: Long = System.currentTimeMillis()) {
        sp.edit().putLong(KEY_DRIVE_LAST, now).apply()
        _driveLastAt.value = now
        markBackup(now)
    }

    var driveEnabled: Boolean
        get() = sp.getBoolean(KEY_DRIVE_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_DRIVE_ENABLED, value).apply()

    var driveAccount: String?
        get() = sp.getString(KEY_DRIVE_ACCOUNT, null)
        set(value) = sp.edit().putString(KEY_DRIVE_ACCOUNT, value).apply()

    var driveFolderId: String?
        get() = sp.getString(KEY_DRIVE_FOLDER, null)
        set(value) = sp.edit().putString(KEY_DRIVE_FOLDER, value).apply()

    /** Fingerprint of the data last uploaded, so unchanged data is not uploaded again. */
    var driveFingerprint: String?
        get() = sp.getString(KEY_DRIVE_FINGERPRINT, null)
        set(value) = sp.edit().putString(KEY_DRIVE_FINGERPRINT, value).apply()

    var driveNeedsReconnect: Boolean
        get() = sp.getBoolean(KEY_DRIVE_RECONNECT, false)
        set(value) = sp.edit().putBoolean(KEY_DRIVE_RECONNECT, value).apply()

    /** Speech recognition language: "en-IN" or "te-IN". */
    var voiceLanguage: String
        get() = sp.getString(KEY_VOICE_LANGUAGE, VOICE_ENGLISH) ?: VOICE_ENGLISH
        set(value) = sp.edit().putString(KEY_VOICE_LANGUAGE, value).apply()

    companion object {
        const val VOICE_ENGLISH = "en-IN"
        const val VOICE_TELUGU = "te-IN"

        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_VOICE_LANGUAGE = "voice_language"
        private const val KEY_DRIVE_LAST = "drive_last_at"
        private const val KEY_DRIVE_ENABLED = "drive_enabled"
        private const val KEY_DRIVE_ACCOUNT = "drive_account"
        private const val KEY_DRIVE_FOLDER = "drive_folder_id"
        private const val KEY_DRIVE_FINGERPRINT = "drive_fingerprint"
        private const val KEY_DRIVE_RECONNECT = "drive_needs_reconnect"
    }
}
