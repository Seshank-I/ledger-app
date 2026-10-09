package com.simpleledger.app.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Locks the app at start-up and after it has been in the background for more than a minute. */
class LockState(initiallyLocked: Boolean) {
    private val _locked = MutableStateFlow(initiallyLocked)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundAt = 0L

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(pinEnabled: Boolean) {
        if (!pinEnabled) {
            _locked.value = false
        } else if (backgroundAt != 0L && SystemClock.elapsedRealtime() - backgroundAt > RELOCK_AFTER_MS) {
            _locked.value = true
        }
    }

    fun unlock() {
        _locked.value = false
    }

    private companion object {
        const val RELOCK_AFTER_MS = 60_000L
    }
}
