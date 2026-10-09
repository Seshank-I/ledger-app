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

    private companion object {
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_BIOMETRIC = "biometric_enabled"
    }
}
