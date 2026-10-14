package app.opentv.ui.vod

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.opentv.ui.VodViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Assert actual Compose controls, not merely whether an Android process survives. */
@RunWith(AndroidJUnit4::class)
class VodUiContractTest {
    @get:Rule val compose = createComposeRule()

    private fun movie() = VodViewModel.ResumeItem(
        mediaKey = "movie:7", title = "Fixture Film", posterUrl = null,
        streamUrl = "http://example.invalid/fixture.mp4",
        progress = 0.4f, positionMillis = 40_000L,
        durationMillis = 100_000L, movieDetailId = 7L,
    )

    private fun series() = VodViewModel.ResumeItem(
        mediaKey = "ep:17", title = "Fixture Series", posterUrl = null,
        streamUrl = "http://example.invalid/fixture.mp4",
        progress = 0.2f, positionMillis = 20_000L,
        durationMillis = 100_000L, seriesDetailId = 9L,
        historyGroupKey = "series:9", episodeLabel = "S01 E03",
    )

    @Test fun posterRendersAndOpensOnClick() {
        var selected = 0
        compose.setContent {
            MaterialTheme {
                PosterCard("Fixture Film", null, onClick = { selected++ },
                    modifier = Modifier.testTag("poster-test"), subtitle = "2026")
            }
        }
        compose.onNodeWithTag("poster:Fixture Film").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun shortClickResumeDoesNotOpenOptions() {
        var resumed = 0
        compose.setContent {
            MaterialTheme {
                ResumeCard(movie(), onOpenDetails = {}, onResetProgress = {},
                    onClick = { resumed++ })
            }
        }
        compose.onNodeWithTag("continue:movie:7").performClick()
        compose.runOnIdle { assertEquals(1, resumed) }
        compose.onNodeWithText("Continue Watching options").assertDoesNotExist()
    }

    @Test fun movieLongPressOffersResetWithConfirmation() {
        var resets = 0
        compose.setContent {
            MaterialTheme {
                ResumeCard(movie(), onOpenDetails = {}, onResetProgress = { resets++ },
                    onClick = {})
            }
        }
        compose.onNodeWithTag("continue:movie:7").performTouchInput { longClick() }
        compose.onNodeWithText("Continue Watching options").assertIsDisplayed()
        compose.onNodeWithText("Remove and reset progress").performClick()
        compose.onNodeWithText("Reset watching progress?").assertIsDisplayed()
        compose.onNodeWithText("Reset progress").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }

    @Test fun seriesLongPressOpensDetailsWithoutResuming() {
        var details = 0
        var resume = 0
        compose.setContent {
            MaterialTheme {
                ResumeCard(series(), onOpenDetails = { details++ },
                    onResetProgress = {}, onClick = { resume++ })
            }
        }
        compose.onNodeWithTag("continue:ep:17").performTouchInput { longClick() }
        compose.onNodeWithText("Open title page").performClick()
        compose.runOnIdle {
            assertEquals(1, details)
            assertEquals(0, resume)
        }
    }
}
