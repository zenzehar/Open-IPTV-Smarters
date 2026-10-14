package app.opentv.sync

import android.content.Context
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
import java.util.concurrent.TimeUnit

/**
 * Cloud rendezvous is durable: each device uploads its own snapshot to Nextcloud.
 * WorkManager is best-effort (Doze may defer checks), not an exact wall-clock timer.
 */
internal class CloudSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = ServiceLocator.get(applicationContext)
        return when (CloudRelaySync(applicationContext, graph).sync()) {
            is CloudRelaySync.Result.Success, CloudRelaySync.Result.NotConfigured -> Result.success()
            is CloudRelaySync.Result.Failed -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "koky-sosy-cloud-periodic"
        private const val ON_DEMAND_NAME = "koky-sosy-cloud-on-demand"
        private fun network() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val work = WorkManager.getInstance(context)
            if (CloudCredentialVault(context).load() == null) {
                work.cancelUniqueWork(PERIODIC_NAME)
                work.cancelUniqueWork(ON_DEMAND_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun syncNow(context: Context) {
            if (CloudCredentialVault(context).load() == null) return
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(network()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ON_DEMAND_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
