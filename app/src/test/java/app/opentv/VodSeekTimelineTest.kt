package app.opentv

import app.opentv.player.VodSeekTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodSeekTimelineTest {
    @Test fun unknownDecoderDurationUsesCatalogMovieRuntime() {
        assertEquals(7_200_000L, VodSeekTimeline.durationMillis(-9223372036854775807L, 7_200_000L, 0L))
    }

    @Test fun episodeAndMovieUseSameKnownDurationPrecedence() {
        assertEquals(6_900_000L, VodSeekTimeline.durationMillis(6_900_000L, 7_200_000L, 0L))
    }

    @Test fun progressCanMoveWhenMovieDurationIsUnavailable() {
        assertTrue(VodSeekTimeline.seekBarMaxMillis(0L, 0L, 0L) > 10_000L)
        assertTrue(VodSeekTimeline.seekBarMaxMillis(0L, 0L, 300_000L) > 300_000L)
    }

    @Test fun unknownMovieLengthIsNotFabricatedAsMetadata() {
        assertEquals(0L, VodSeekTimeline.durationMillis(-1L, 0L, 0L))
    }

    @Test fun prefersSavedHistoryIfOtherDurationsMissing() {
        assertEquals(6_800_000L, VodSeekTimeline.durationMillis(-1L, 0L, 6_800_000L))
    }

    @Test fun timelineClampAndSeekAcknowledgementAreShared() {
        assertEquals(0L, VodSeekTimeline.clampTarget(-10L, 0L))
        assertEquals(80_000L, VodSeekTimeline.clampTarget(120_000L, 80_000L))
        assertTrue(VodSeekTimeline.reachedTarget(119_000L, 120_000L))
        assertFalse(VodSeekTimeline.reachedTarget(60_000L, 120_000L))
    }
}
