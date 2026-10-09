package com.simpleledger.app

import android.content.Context
import com.simpleledger.app.backup.BackupManager
import com.simpleledger.app.data.AppDatabase
import com.simpleledger.app.data.AppPrefs
import com.simpleledger.app.data.LedgerRepository
import com.simpleledger.app.security.LockState
import com.simpleledger.app.security.PinManager

/** Creates the app's long-lived objects once. Screens get them through [LedgerApp]. */
class AppContainer(context: Context) {
    val db = AppDatabase.build(context)
    val repo = LedgerRepository(db)
    val prefs = AppPrefs(context)
    val pin = PinManager(prefs)
    val lock = LockState(initiallyLocked = pin.isEnabled())
    val backup = BackupManager(context, db, repo, prefs)
}
