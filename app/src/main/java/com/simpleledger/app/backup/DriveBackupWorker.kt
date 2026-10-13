package com.simpleledger.app.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.simpleledger.app.LedgerApp
import java.util.concurrent.TimeUnit

/** Runs once a day when the phone has internet; uploads only if something changed. */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as LedgerApp).container
        if (!c.prefs.driveEnabled) return Result.success()
        return when (c.drive.run(applicationContext)) {
            DriveBackup.Outcome.Done, DriveBackup.Outcome.NothingNew -> Result.success()
            // Google wants the user to approve again; Settings and the home banner will ask them.
            is DriveBackup.Outcome.NeedsApproval -> {
                c.prefs.driveNeedsReconnect = true
                Result.success()
            }
            is DriveBackup.Outcome.Failed -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "drive-backup"
        private const val MAX_RETRIES = 5

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
