package app.opentv.qa

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * An actual Media3 + Android decoder seek against a synthetic HTTP MP4.
 * The test asserts playback readiness and position, not decoded pixels, hardware
 * codec compatibility on a physical TV, or a particular commercial provider.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class Media3SyntheticSeekTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val stream = "http://10.0.2.2:18080/movie/fixture/testonly/1.mp4"

    @Test(timeout = 90000)
    fun seekMultipleTimesAndRemainReady() {
        val probe = URL("http://10.0.2.2:18080/health").openConnection() as HttpURLConnection
        val available = try {
            probe.connectTimeout = 4000
            probe.readTimeout = 4000
            val body = probe.inputStream.bufferedReader().readText()
            body.contains("\"media\": true") || body.contains("\"media\":true")
        } finally { probe.disconnect() }
        assumeTrue("SKIPPED: ffmpeg synthetic video unavailable on CI host", available)

        val error = AtomicReference<PlaybackException?>()
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build()
            player.addListener(object : Player.Listener {
                override fun onPlayerError(exception: PlaybackException) { error.set(exception) }
            })
            player.setMediaItem(MediaItem.fromUri(stream))
            player.prepare()
            player.playWhenReady = true
        }
        try {
            fun snapshot(): Pair<Int, Long> {
                var result = Pair(Player.STATE_IDLE, 0L)
                instrumentation.runOnMainSync {
                    result = Pair(player.playbackState, player.currentPosition)
                }
                return result
            }
            fun awaitReady(timeout: Long): Boolean {
                val end = SystemClock.uptimeMillis() + timeout
                while (SystemClock.uptimeMillis() < end) {
                    error.get()?.let { throw AssertionError("Decoder failure: " + it.errorCodeName, it) }
                    if (snapshot().first == Player.STATE_READY) return true
                    Thread.sleep(200)
                }
                return false
            }
            assertTrue("Synthetic MP4 did not start in Media3", awaitReady(25000))
            for (target in listOf(9000L, 3000L, 12000L)) {
                instrumentation.runOnMainSync { player.seekTo(target) }
                assertTrue("Media3 not ready after seek to " + target, awaitReady(15000))
                assertTrue("Media3 position did not follow seek to " + target,
                    snapshot().second >= target - 1500L)
            }
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }
}
