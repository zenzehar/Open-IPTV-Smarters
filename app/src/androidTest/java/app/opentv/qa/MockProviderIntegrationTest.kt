package app.opentv.qa

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opentv.MainActivity
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue

/** Real Android + Room + fixture-provider tests. Never uses a user's IPTV account. */
@RunWith(AndroidJUnit4::class)
class MockProviderIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val source get() = Source(name = "Synthetic Test Only",
        kind = SourceKind.XTREAM, url = "http://10.0.2.2:18080",
        username = "fixture", password = "testonly")
    private val graph get() = ServiceLocator.get(context)

    @Test fun providerAuthenticationAndAccountExpiry() = runBlocking {
        val result = graph.sourceRepository.testDetailed(source)
        assertTrue("Synthetic provider auth failed", result.isSuccess)
        assertNotNull(result.getOrNull()?.expiryMillis)
        assertTrue("Invalid credentials must fail",
            graph.sourceRepository.testDetailed(source.copy(password = "wrong")).isFailure)
    }

    @Test fun liveCatalogueGuideAndEpisodeParsing() = runBlocking {
        val id = graph.sourceRepository.save(source)
        val saved = source.copy(id = id)
        val response = graph.catalogRepository.syncLive(saved, System.currentTimeMillis())
        assertTrue("Live sync failed: $response",
            response is app.opentv.data.repo.CatalogRepository.SyncResult.Success)
        val channels = graph.catalogRepository.observeChannels(id).first()
        assertEquals("Mock live catalogue", 12, channels.size)
        assertEquals(2, graph.xtreamApi.liveCategories(saved).size)
        assertEquals(9, graph.xtreamApi.series(saved).size)
        assertEquals(2, graph.xtreamApi.episodes(saved, "1").size)
    }

    @Test fun entireCachedMovieCatalogueSearchAndPagination() = runBlocking {
        val id = graph.sourceRepository.save(source)
        val saved = source.copy(id = id)
        val sync = graph.catalogRepository.syncVod(saved, System.currentTimeMillis())
        assertNull("Movie sync error", sync.movieError)
        assertNull("Series sync error", sync.seriesError)
        val first = graph.catalogRepository.observeMoviesPage(null, id, "", 161, 0).first()
        val last = graph.catalogRepository.observeMoviesPage(null, id, "", 161, 2240).first()
        assertEquals(161, first.size)
        assertEquals("Paged past title 2000", 65, last.size)
        val search = graph.catalogRepository.observeMoviesPage(null, id, "Fixture Movie 2305", 161, 0).first()
        assertEquals(1, search.size)
        assertTrue(search.first().name.contains("2305"))
        assertEquals(9, graph.catalogRepository.observeSeriesPage(null, id, "Fixture Series", 161, 0).first().size)
    }

    @Test fun syntheticVideoSupportsHttpByteRangeSeeking() {
        val request = Request.Builder().url("http://10.0.2.2:18080/movie/fixture/testonly/1.mp4")
            .header("Range", "bytes=400-499").build()
        OkHttpClient().newCall(request).execute().use { response ->
            assumeTrue("Synthetic MP4 unavailable; codec test skipped", response.code != 503)
            assertEquals(206, response.code)
            assertEquals(100L, response.body?.contentLength())
            assertEquals("bytes", response.header("Accept-Ranges"))
        }
    }

    @Test fun activityStartsOnAndroidFramework() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            instrumentation.waitForIdleSync()
            assertEquals("app.opentv.qa", activity.packageName)
            assertFalse(activity.isFinishing)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
