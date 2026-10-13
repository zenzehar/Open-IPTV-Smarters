package app.opentv

import app.opentv.player.VodSeekRecovery
import org.junit.Assert.assertEquals
import org.junit.Test

class VodSeekRecoveryTest {
    @Test fun moviesRestartAtNewPosition() {
        assertEquals(2_400_000L, VodSeekRecovery.retryStart(60_000L, 2_400_000L, false))
    }
    @Test fun episodesUseIdenticalSeekSemantics() {
        assertEquals(1_200_000L, VodSeekRecovery.retryStart(0L, 1_200_000L, false))
    }
    @Test fun livePositionIsUnaffected() {
        assertEquals(0L, VodSeekRecovery.retryStart(0L, 1_200_000L, true))
    }
    @Test fun negativePositionsAreClamped() {
        assertEquals(0L, VodSeekRecovery.retryStart(60_000L, -5L, false))
    }
}
