package app.opentv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

/**
 * Native VLC constructor/release smoke, repeated to expose ABI or shutdown faults.
 * Does not claim codec/surface playback or hardware-decoder validation.
 */
@RunWith(AndroidJUnit4::class)
class LibVlcNativeLifecycleTest {
    @Test fun nativeDecoderConstructsAndReleasesRepeatedly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repeat(3) {
            val libVlc = LibVLC(context.applicationContext, arrayListOf("--no-osd"))
            try {
                val player = MediaPlayer(libVlc)
                try { assertNotNull(player.vlcVout) }
                finally { player.release() }
            } finally { libVlc.release() }
        }
    }
}
