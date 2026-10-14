package app.opentv

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Platform decoder and Media3 timestamp test using a synthetic local MP4.
 * Proves Media3 reads the duration and accepts seeks; does not assert actual
 * frames rendered or claim compatibility with a real external IPTV provider.
 */
@RunWith(AndroidJUnit4::class)
class LocalVideoSeekTest {
    @Test fun localMovieHasFiniteDurationAndSeeksInBothDirections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val file = File(target.cacheDir, "koky-qa-video.mp4")
        instrumentation.context.assets.open("qa_seek_fixture.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(target).build().also {
                    it.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    it.prepare()
                }
            }
            var ready = false
            for (attempt in 0..100) {
                instrumentation.runOnMainSync {
                    ready = player!!.playbackState == Player.STATE_READY
                }
                if (ready) break
                SystemClock.sleep(100)
            }
            assertTrue("Media3 did not prepare the synthetic MP4", ready)
            var duration = -1L
            instrumentation.runOnMainSync { duration = player!!.duration }
            assertTrue("MP4 must have a finite duration", duration >= 4_000L)
            for (targetMs in listOf(2_000L, 500L, 3_000L)) {
                var position = -1L
                instrumentation.runOnMainSync {
                    player!!.seekTo(targetMs)
                    position = player!!.currentPosition
                }
                assertTrue("Seek to $targetMs remained at $position",
                    kotlin.math.abs(targetMs - position) <= 1_000L)
            }
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            file.delete()
        }
    }
}
