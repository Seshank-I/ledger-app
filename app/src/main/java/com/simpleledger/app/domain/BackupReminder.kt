package com.simpleledger.app.domain

import com.simpleledger.app.data.LedgerEntryEntity

private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

/** True when some entries are not in any backup and have been waiting for more than 7 days. */
fun needsBackupReminder(entries: List<LedgerEntryEntity>, lastBackupAt: Long, now: Long): Boolean {
    val notBackedUp = entries.filter { it.recordedAt > lastBackupAt }
    if (notBackedUp.isEmpty()) return false
    val waitingSince = maxOf(lastBackupAt, notBackedUp.minOf { it.recordedAt })
    return now - waitingSince > SEVEN_DAYS_MS
}
