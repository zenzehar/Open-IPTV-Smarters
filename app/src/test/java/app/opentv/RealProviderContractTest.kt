package app.opentv

import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.remote.XtreamApi
import java.net.URI
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Opt-in REAL provider test. Credentials only from protected GitHub environment
 * secrets. No provider values, titles, URLs, usernames, passwords or exceptions
 * can appear in assertion messages. Skipped in ordinary PR/unit-test builds.
 *
 * Do not add system-property fallbacks: Gradle command lines are logged by CI.
 */
class RealProviderContractTest {
    private lateinit var api: XtreamApi
    private lateinit var source: Source

    @Before fun configureOnlyWithPrivateSecrets() {
        val raw = System.getenv("KOKY_PROVIDER_URL").orEmpty()
        val user = System.getenv("KOKY_PROVIDER_USERNAME").orEmpty()
        val password = System.getenv("KOKY_PROVIDER_PASSWORD").orEmpty()
        assumeTrue("Protected live-provider test not configured in this build",
            raw.isNotBlank() && user.isNotBlank() && password.isNotBlank())

        val uri = try { URI(raw.trim()) } catch (_: Exception) {
            throw AssertionError("Protected provider address invalid (details suppressed)")
        }
        val originalScheme = uri.scheme?.lowercase()
        val allowHttp = System.getenv("KOKY_ALLOW_HTTP").equals("true", ignoreCase = true)
        if (uri.host.isNullOrBlank() || originalScheme !in setOf("http", "https") ||
            uri.rawUserInfo != null) {
            throw AssertionError("Protected provider address invalid (details suppressed)")
        }
        val scheme = if (originalScheme == "http" && !allowHttp) "https" else originalScheme!!
        val port = if (originalScheme == "http" && !allowHttp && uri.port == 80) -1 else uri.port
        val safeBase = URI(scheme, null, uri.host, port, null, null, null).toString()
            .trimEnd('/')

        source = Source(
            name = "Protected external QA (never persisted)",
            kind = SourceKind.XTREAM,
            url = safeBase,
            username = user,
            password = password,
        )
        // Never automatically follow redirects: a cross-origin Location could
        // forward username and password in the original API request URL.
        api = XtreamApi(OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .build())
    }

    private suspend fun <T> guarded(label: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (_: Exception) {
            // No throwable cause or response body in JUnit output.
            throw AssertionError("$label failed (provider details suppressed)")
        }
    }

    @Test fun protectedProviderAuthAndRealCatalogues() = runBlocking<Unit> {
        guarded("Authentication") { api.authenticate(source) }
        val movies = guarded("Movie catalogue") { api.movies(source) }
        assertTrue("Real movie catalogue contained no playable entries", movies.isNotEmpty())
        assertTrue("Movie entries lacked stream identifiers", movies.any { it.streamId.isNotBlank() })

        val series = guarded("Series catalogue") { api.series(source) }
        assertTrue("Real series catalogue was empty", series.isNotEmpty())
        val channels = guarded("Live catalogue") { api.liveStreams(source) }
        assertTrue("Real Live catalogue was empty", channels.isNotEmpty())

        val movieCats = guarded("Movie categories") { api.movieCategories(source) }
        assertTrue("Real movie categories were empty", movieCats.isNotEmpty())
        guarded("Series categories") { api.seriesCategories(source) }
        guarded("Live categories") { api.liveCategories(source) }

        val episodeList = guarded("Season/episode metadata") {
            api.episodes(source, series.first().seriesId)
        }
        assertTrue("A sample series did not return episodes", episodeList.isNotEmpty())
    }
}
