/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.opentv.core.ServiceLocator
import app.opentv.sync.NasSync
import java.util.concurrent.TimeUnit

/**
 * Store-and-forward same-network synchronization via the user's preconfigured SMB/NAS share.
 * An always-available NAS (not another sleeping Android box) holds a durable JSON envelope for
 * each device, so devices never have to be running at the same time.
 *
 * WorkManager's minimum periodic interval is 15 minutes and actual execution may be delayed
 * by Android standby/network policy. All traffic stays between device and configured NAS.
 * User-invoked LAN Share/Receive and NAS Sync Now are deliberately unchanged.
 */
class NasAutoSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val graph = ServiceLocator.get(applicationContext)
        if (!graph.settings.nasAutoSync.value) return Result.success()
        return when (val outcome = NasSync(graph).sync()) {
            is NasSync.Result.Success -> {
                Log.i(TAG, "Merged ${outcome.merged} items from ${outcome.peers} NAS peers")
                Result.success()
            }
            is NasSync.Result.NotConfigured -> Result.success()
            is NasSync.Result.Failed -> {
                Log.w(TAG, "NAS sync unavailable: ${outcome.message ?: "retry later"}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "NasAutoSync"
        private const val WORK_NAME = "koky-nas-auto-sync"

        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<NasAutoSyncWorker>(
                15, TimeUnit.MINUTES,
            )
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
