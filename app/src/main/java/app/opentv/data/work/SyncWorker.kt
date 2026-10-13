/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.opentv.core.ServiceLocator
import app.opentv.data.repo.CatalogRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.withLock
import androidx.work.workDataOf

/**
 * Background refresh of the catalogue and the guide.
 *
 * Runs on WorkManager rather than a foreground timer so it survives the app being killed,
 * which on a TV box happens constantly. Failures return [Result.retry] with WorkManager's
 * exponential backoff — the app never hammers a provider that is already struggling.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = ServiceLocator.get(applicationContext)
        // Serialize against the UI and other worker instances, then RECHECK due times.
        // A periodic job and a startup job may both run, but only one downloads.
        return graph.catalogueRefreshMutex.withLock {
            val now = System.currentTimeMillis()
            val sources = graph.sourceRepository.enabled()
            if (sources.isEmpty()) return@withLock Result.success()
            val settings = graph.settings
            val force = inputData.getBoolean("force_refresh", false)
            val hours = settings.catalogueUpdateHours.value
            val legacyLive = sources.minOfOrNull { it.lastCatalogSyncMillis } ?: 0L
            val liveStamp = settings.liveUpdated.value.takeIf { it > 0L } ?: legacyLive
            val moviesStamp = settings.moviesUpdated.value.takeIf { it > 0L } ?: settings.vodSyncedAtMillis
            val seriesStamp = settings.seriesUpdated.value.takeIf { it > 0L } ?: settings.vodSyncedAtMillis
            val liveDue = settings.liveEnabled.value &&
                (force || CatalogueRefreshPolicy.isDue(liveStamp, hours, now))
            val moviesDue = settings.moviesEnabled.value &&
                (force || CatalogueRefreshPolicy.isDue(moviesStamp, hours, now))
            val seriesDue = settings.seriesEnabled.value &&
                (force || CatalogueRefreshPolicy.isDue(seriesStamp, hours, now))
            if (!liveDue && !moviesDue && !seriesDue) return@withLock Result.success()

            var liveFailed = false
            var moviesFailed = false
            var seriesFailed = false
            for (source in sources) {
                if (liveDue) {
                    when (val result = graph.catalogRepository.syncLive(source, now)) {
                        is CatalogRepository.SyncResult.Success ->
                            Log.i(TAG, "Live for ${source.name}: ${result.channelCount} channels")
                        is CatalogRepository.SyncResult.Failed -> {
                            liveFailed = true
                            Log.w(TAG, "Live for ${source.name} failed: ${result.reason}")
                        }
                    }
                }
                if ((moviesDue || seriesDue) &&
                    source.kind == app.opentv.data.model.SourceKind.XTREAM) {
                    val result = graph.catalogRepository.syncVod(
                        source, now, includeMovies = moviesDue, includeSeries = seriesDue,
                    )
                    if (moviesDue && result.movieError != null) moviesFailed = true
                    if (seriesDue && result.seriesError != null) seriesFailed = true
                }
            }
            if (liveDue && !liveFailed) settings.recordCatalogueUpdated("live")
            if (moviesDue && !moviesFailed) settings.recordCatalogueUpdated("movies")
            if (seriesDue && !seriesFailed) settings.recordCatalogueUpdated("series")
            if ((moviesDue || seriesDue) && !moviesFailed && !seriesFailed)
                settings.vodSyncedAtMillis = now

            if (liveDue) {
                val summary = graph.epgRepository.syncAll(now)
                if (summary.feedsFailed > 0) liveFailed = true
                runCatching { graph.recordingEngine.rescanSeriesRules() }
            }
            if (liveFailed || moviesFailed || seriesFailed) Result.retry()
            else Result.success()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "opentv-periodic-sync"
        private const val ONE_SHOT_WORK_NAME = "opentv-manual-sync"

        /**
         * Kick a single catalogue + guide refresh now, off the UI. Runs the same [doWork] as the
         * periodic job — so it honours the content-type toggles — but as one-time work, which means
         * it survives the user navigating away from the screen that triggered it (unlike a refresh
         * tied to a screen's ViewModel scope). REPLACE so repeated taps coalesce into one run.
         */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf("force_refresh" to true))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** A single stale-only check on app start. The mutex and stage stamps prevent
         * another fetch when periodic work happens to launch at the same moment. */
        fun refreshDueAtStartup(context: Context) {
            if (app.opentv.core.AppSettings.get(context).catalogueUpdateHours.value == 0) return
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "opentv-startup-due-sync", ExistingWorkPolicy.KEEP, request,
            )
        }

        fun schedule(context: Context) {
            val interval = app.opentv.core.AppSettings.get(context).catalogueUpdateHours.value.toLong()
            val manager = WorkManager.getInstance(context)
            if (interval == 0L) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
