package de.kevinkaupert.gymtracker

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class GymTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setupBackupWorker()
    }

    private fun setupBackupWorker() {
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }
}
