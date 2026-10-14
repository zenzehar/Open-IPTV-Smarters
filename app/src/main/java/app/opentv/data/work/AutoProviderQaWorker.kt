package app.opentv.data.work

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
import app.opentv.data.model.SourceKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Autonomous read-only provider QA on the **device with its existing saved login**.
 *
 * No credentials, account names, titles, request URLs, exceptions, or media bytes are
 * persisted, sent to a telemetry service, or written to Android logs. A tiny same-host
 * HTTP Range request checks whether the server honours seeking without running video
 * playback and disrupting a TV programme. No catalogue refresh or database writes.
 */
class AutoProviderQaWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_RUN, 0L)
        if (!ProviderQaPolicy.isDue(last, System.currentTimeMillis())) return Result.success()

        val graph = ServiceLocator.get(applicationContext)
        val sources = try {
            graph.sourceRepository.enabled().filter { it.kind == SourceKind.XTREAM }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the previous valid report and allow WorkManager to try again later.
            return Result.retry()
        }
        if (sources.isEmpty()) return Result.success()

        val report = JSONObject()
        val entries = JSONArray()
        report.put("schema", 1)
        report.put("completedAtMs", System.currentTimeMillis())
        report.put("providerCount", sources.size)
        var failures = 0
        val api = graph.xtreamApi
        for ((index, source) in sources.withIndex()) {
            val entry = JSONObject()
            val checks = JSONObject()
            entry.put("provider", index + 1)
            suspend fun check(name: String, block: suspend () -> Boolean): Boolean {
                val passed = try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                checks.put(name, if (passed) "PASS" else "FAIL")
                if (!passed) failures++
                return passed
            }
            val authed = check("authentication") {
                api.authenticate(source)
                true
            }
            if (authed) {
                check("live_categories") { api.liveCategories(source).isNotEmpty() }
                check("movie_categories") { api.movieCategories(source).isNotEmpty() }
                check("series_categories") { api.seriesCategories(source).isNotEmpty() }
                val sampleSeries = try {
                    graph.database.series()
                        .observePage(null, source.id, "", 1, 0).first()
                        .firstOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { null }
                if (sampleSeries == null) {
                    checks.put("episodes", "WAITING_FOR_CATALOGUE")
                } else {
                    check("episodes") { api.episodes(source, sampleSeries.seriesId).isNotEmpty() }
                }

                val sampleMovie = try {
                    graph.database.movies()
                        .observePage(null, source.id, "", 1, 0).first()
                        .firstOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { null }
                if (sampleMovie == null) {
                    checks.put("movie_http_range", "WAITING_FOR_CATALOGUE")
                } else {
                    val url = sampleMovie.streamUrl.toHttpUrlOrNull()
                    val providerUrl = source.url.toHttpUrlOrNull()
                    if (url == null || providerUrl == null ||
                        url.host != providerUrl.host || url.port != providerUrl.port) {
                        checks.put("movie_http_range", "SKIPPED_DIFFERENT_HOST")
                    } else {
                        check("movie_http_range") {
                            val client = graph.httpClient.newBuilder()
                                .followRedirects(false)
                                .followSslRedirects(false)
                                .connectTimeout(6, TimeUnit.SECONDS)
                                .readTimeout(8, TimeUnit.SECONDS)
                                .callTimeout(12, TimeUnit.SECONDS)
                                .build()
                            val request = Request.Builder()
                                .url(url)
                                .header("Range", "bytes=0-4095")
                                .header("User-Agent", source.userAgent)
                                .build()
                            client.newCall(request).execute().use { response ->
                                // 200 means the provider ignored Range; not a seek pass.
                                // We never read or save movie bytes; close the connection.
                                response.code == 206 &&
                                    response.header("Content-Range")?.startsWith("bytes 0-") == true
                            }
                        }
                    }
                }
            } else {
                checks.put("other_tests", "BLOCKED_BY_AUTHENTICATION")
            }
            entry.put("checks", checks)
            entries.put(entry)
        }
        report.put("checks", entries)
        report.put("failures", failures)
        // JSON includes only fixed labels, enum-like states, counts and a timestamp.
        prefs.edit()
            .putString(LAST_REPORT, report.toString())
            .putLong(LAST_RUN, System.currentTimeMillis())
            .apply()
        return Result.success()
    }

    companion object {
        private const val PREFS = "koky_provider_qa"
        private const val LAST_REPORT = "sanitized_report"
        private const val LAST_RUN = "last_run_ms"
        private const val PERIODIC_NAME = "koky-provider-qa-daily"
        private const val STARTUP_NAME = "koky-provider-qa-on-launch"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoProviderQaWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints).build(),
            )
            WorkManager.getInstance(context).enqueueUniqueWork(
                STARTUP_NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AutoProviderQaWorker>()
                    .setInitialDelay(90, TimeUnit.SECONDS)
                    .setConstraints(constraints).build(),
            )
        }

        /** Fixed-schema PASS/FAIL details only; never displays source names, URLs, or credentials. */
        fun lastDetails(context: Context): String {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(LAST_REPORT, null) ?: return "No provider checks have finished yet."
            return try {
                val rows = JSONObject(raw).getJSONArray("checks")
                buildString {
                    for (i in 0 until rows.length()) {
                        if (isNotEmpty()) append("\n")
                        append("Provider ").append(i + 1).append(": ")
                        val checks = rows.getJSONObject(i).getJSONObject("checks")
                        val keys = listOf("authentication", "live_categories",
                            "movie_categories", "series_categories", "episodes",
                            "movie_http_range", "other_tests")
                        append(keys.filter { checks.has(it) }.joinToString(" | ") { key ->
                            "$key: ${checks.optString(key)}"
                        })
                    }
                }
            } catch (_: Exception) {
                "No diagnostic details available"
            }
        }

        fun lastSummary(context: Context): String {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(LAST_REPORT, null) ?: return "Awaiting first automatic provider check"
            return try {
                val report = JSONObject(raw)
                val entries = report.getJSONArray("checks")
                val timestamp = java.text.DateFormat.getDateTimeInstance()
                    .format(java.util.Date(report.getLong("completedAtMs")))
                val passed = (0 until entries.length()).sumOf { idx ->
                    val checks = entries.getJSONObject(idx).getJSONObject("checks")
                    checks.keys().asSequence()
                        .count { key -> checks.optString(key) == "PASS" }
                }
                val failed = report.optInt("failures")
                "Last check: $timestamp. $passed passed, $failed failed. " +
                    "${report.optInt("providerCount")} provider(s). " +
                    (if (failed == 0) "No failures detected in supported checks."
                     else "One or more provider checks failed.")
            } catch (_: Exception) {
                "Automatic provider check report unavailable"
            }
        }
    }
}

internal object ProviderQaPolicy {
    const val INTERVAL_MS = 24L * 60L * 60L * 1_000L

    fun isDue(lastMs: Long, nowMs: Long): Boolean =
        lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= INTERVAL_MS
}
