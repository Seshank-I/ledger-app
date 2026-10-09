package com.simpleledger.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpleledger.app.ui.LedgerNavHost
import com.simpleledger.app.ui.screens.LockScreen
import com.simpleledger.app.ui.theme.SimpleLedgerTheme

// FragmentActivity (rather than ComponentActivity) is needed for the fingerprint prompt.
class MainActivity : FragmentActivity() {
    private val container get() = (application as LedgerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleLedgerTheme {
                val locked by container.lock.locked.collectAsStateWithLifecycle()
                Box {
                    LedgerNavHost()
                    if (locked) LockScreen(onUnlock = container.lock::unlock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.lock.onForeground(container.pin.isEnabled())
    }

    override fun onStop() {
        super.onStop()
        container.lock.onBackground()
    }
}
