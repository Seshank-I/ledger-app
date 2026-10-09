package com.simpleledger.app.security

import android.util.Base64
import com.simpleledger.app.data.AppPrefs
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Optional 4-digit PIN. Only a salted PBKDF2 hash is stored, never the PIN itself. */
class PinManager(private val prefs: AppPrefs) {
    fun isEnabled(): Boolean = prefs.pinHash != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.setPin(hash(pin, salt), Base64.encodeToString(salt, Base64.NO_WRAP))
    }

    fun verify(pin: String): Boolean {
        val stored = prefs.pinHash ?: return false
        val salt = Base64.decode(prefs.pinSalt ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())
    }

    fun disable() = prefs.clearPin()

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    private companion object {
        const val ITERATIONS = 50_000
    }
}
