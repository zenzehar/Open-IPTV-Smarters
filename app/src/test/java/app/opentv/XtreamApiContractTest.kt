package app.opentv

import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.model.StreamKind
import app.opentv.data.remote.XtreamApi
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Real XtreamApi HTTP parser and URL builder exercised against a private local panel.
 * No production provider, credentials, network access or live media are needed.
 */
class XtreamApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: XtreamApi
    private lateinit var source: Source

    @Before fun startServer() {
        server = MockWebServer()
        server.start()
        source = Source(id = 19L, name = "QA Panel", kind = SourceKind.XTREAM,
            url = server.url("/").toString().trimEnd('/'),
            username = "qa_user", password = "qa_password",
            userAgent = "KokyQA/1.0")
        api = XtreamApi(OkHttpClient.Builder().build())
    }

    @After fun stopServer() = server.shutdown()

    private fun json(text: String, code: Int = 200): MockResponse =
        MockResponse().setResponseCode(code)
            .setHeader("Content-Type", "application/json; charset=utf-8").setBody(text)

    @Test fun activeAccountParsesExpiryAndUsesConfiguredUserAgent() = runBlocking {
        server.enqueue(json("""{"user_info":{"auth":"1","status":"Active","username":"qa_user","exp_date":"1800000000","max_connections":"2","active_cons":1},"server_info":{"timezone":"Australia/Adelaide"}}"""))
        val info = api.authenticate(source)
        assertEquals(1_800_000_000_000L, info.expiryMillis)
        assertEquals(2, info.maxConnections)
        assertEquals("Australia/Adelaide", info.timezone)
        val request = server.takeRequest()
        assertEquals("/player_api.php", request.requestUrl?.encodedPath)
        assertEquals("qa_user", request.requestUrl?.queryParameter("username"))
        assertEquals("qa_password", request.requestUrl?.queryParameter("password"))
        assertEquals("KokyQA/1.0", request.getHeader("User-Agent"))
    }

    @Test fun inactiveAndInvalidAccountsAreRejected() = runBlocking {
        server.enqueue(json("""{"user_info":{"auth":0,"status":"Disabled"}}"""))
        try {
            api.authenticate(source)
            fail("Expected inactive Xtream credentials to be rejected")
        } catch (e: XtreamApi.XtreamException) {
            assertTrue(e.message.orEmpty().contains("Disabled"))
        }
        server.enqueue(json("""{"user_info":{"auth":0,"status":"Active"}}"""))
        try {
            api.authenticate(source)
            fail("Expected auth=0 to be rejected")
        } catch (e: XtreamApi.XtreamException) {
            assertTrue(e.message.orEmpty().contains("password"))
        }
    }

    @Test fun serverHttpErrorsAndNonJsonBodiesHaveActionableMessages() = runBlocking {
        server.enqueue(json("{}", 403))
        try {
            api.authenticate(source)
            fail("403 must fail")
        } catch (e: XtreamApi.XtreamException) {
            assertTrue(e.message.orEmpty().contains("403"))
        }
        server.enqueue(json("<html>Service unavailable</html>"))
        try {
            api.authenticate(source)
            fail("HTML error page must fail")
        } catch (e: XtreamApi.XtreamException) {
            assertTrue(e.message.orEmpty().contains("valid catalogue"))
        }
    }

    @Test fun categoriesTolerateMissingAndMixedIdTypes() = runBlocking {
        server.enqueue(json("""[{"category_id":101,"category_name":"Drama"},{"category_id":"102","category_name":"Comedy"},{"category_name":"incomplete"},null]"""))
        val result = api.movieCategories(source)
        assertEquals(2, result.size)
        assertEquals("101", result[0].id)
        assertEquals(19L, result[0].sourceId)
        assertEquals(StreamKind.MOVIE, result[0].kind)
        assertEquals("102", result[1].id)
        assertEquals("get_vod_categories", server.takeRequest().requestUrl?.queryParameter("action"))
    }

    @Test fun mixedTypeMovieIdsAndExtensionsProduceCorrectSeekableUrls() = runBlocking {
        server.enqueue(json("""[{"stream_id":410,"name":"QA Film MP4","category_id":7,"container_extension":"mp4","duration_secs":"7200"},{"stream_id":"411","name":"QA Film MKV","category_id":"7","container_extension":"mkv","duration_secs":3000},{"name":"invalid row"}]"""))
        val titles = api.movies(source)
        assertEquals(2, titles.size)
        assertTrue(titles[0].streamUrl.endsWith("/movie/qa_user/qa_password/410.mp4"))
        assertTrue(titles[1].streamUrl.endsWith("/movie/qa_user/qa_password/411.mkv"))
        assertEquals(7200, titles[0].durationSeconds)
        assertEquals("get_vod_streams", server.takeRequest().requestUrl?.queryParameter("action"))
    }

    @Test fun multiSeasonEpisodesPreserveOrderAndStreamPaths() = runBlocking {
        server.enqueue(json("""{"episodes":{"1":[{"id":"e1","season":"1","episode_num":1,"title":"Pilot","container_extension":"mp4","info":{"duration_secs":"2700"}}],"2":[{"id":202,"season":2,"episode_num":"3","title":"Return","container_extension":"mkv"}]}}"""))
        val episodes = api.episodes(source, "show-24")
        assertEquals(2, episodes.size)
        assertEquals(1, episodes[0].season)
        assertEquals(2, episodes[1].season)
        assertEquals(3, episodes[1].episodeNumber)
        assertEquals(2700, episodes[0].durationSeconds)
        assertTrue(episodes[1].streamUrl.endsWith("/series/qa_user/qa_password/202.mkv"))
        val url = server.takeRequest().requestUrl
        assertEquals("get_series_info", url?.queryParameter("action"))
        assertEquals("show-24", url?.queryParameter("series_id"))
    }

    @Test fun catalogueOverTwoThousandItemsIsParsedWithoutTruncation() = runBlocking {
        val body = (1..2505).joinToString(prefix = "[", postfix = "]") {
            """{"stream_id":$it,"name":"QA Movie $it","category_id":"test","container_extension":"mp4"}"""
        }
        server.enqueue(json(body))
        val movies = api.movies(source)
        assertEquals(2505, movies.size)
        assertEquals("2505", movies.last().streamId)
        assertEquals("QA Movie 2505", movies.last().name)
    }

    @Test fun liveCategoriesAndChannelsUseDistinctSourceKind() = runBlocking {
        server.enqueue(json("""[{"category_id":"sport","category_name":"Sport"}]"""))
        server.enqueue(json("""[{"stream_id":"123","name":"QA Channel","category_id":"sport","num":"8","epg_channel_id":"qa.one"}]"""))
        assertEquals(StreamKind.LIVE, api.liveCategories(source).single().kind)
        val channel = api.liveStreams(source).single()
        assertEquals("sport", channel.categoryId)
        assertEquals("qa.one", channel.epgChannelId)
        assertEquals(8, channel.number)
        assertTrue(channel.streamUrl.endsWith("/live/qa_user/qa_password/123.m3u8"))
    }
}
