package app.opentv.ui.vod

import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opentv.ui.VodViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Simulates actual key-down, held interval and key-up, not a mouse long-click. */
@RunWith(AndroidJUnit4::class)
class RemoteHoldInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun item() = VodViewModel.ResumeItem(
        mediaKey = "movie:77", title = "Remote Hold Fixture",
        posterUrl = null, streamUrl = "http://fixture.invalid/movie.mp4",
        progress = 0.5f, movieDetailId = 77L,
    )

    @Test fun holdingDpadCenterOpensContinueWatchingOptionsInsteadOfResuming() {
        var resumed = 0
        compose.setContent {
            MaterialTheme {
                ResumeCard(item(), onOpenDetails = {}, onResetProgress = {},
                    onClick = { resumed++ })
            }
        }
        compose.onNodeWithTag("continue:movie:77")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // ACTION_UP needs the same downTime as ACTION_DOWN to simulate a hold.
        val downTime = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(downTime, downTime,
            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        Thread.sleep(680L)
        instrumentation.sendKeySync(KeyEvent(downTime,
            android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_DPAD_CENTER, 0))
        compose.waitForIdle()
        compose.onNodeWithText("Continue Watching options").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Long OK must not start playback", 0, resumed) }
    }
}
