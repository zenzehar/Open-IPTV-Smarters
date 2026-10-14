package app.opentv

import app.opentv.player.VodSeekPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VodSeekPolicyTest {
    @Test fun reportedEndCannotFreezePlaybackAt4414() {
        val reportedDuration = 44 * 60_000L
        val actual = 44 * 60_000L + 14_000L
        assertThat(VodSeekPolicy.contradictsDuration(reportedDuration, actual)).isTrue()
        assertThat(VodSeekPolicy.displayMaximum(reportedDuration, actual, true)).isGreaterThan(actual)
        assertThat(VodSeekPolicy.relative(actual, 10_000L, reportedDuration, true))
            .isEqualTo(actual + 10_000L)
    }

    @Test fun unknownDurationNeverClampsForwardSeekToZero() {
        assertThat(VodSeekPolicy.target(2_400_000L, 0L, false)).isEqualTo(2_400_000L)
        assertThat(VodSeekPolicy.displayMaximum(0L, 0L, false)).isAtLeast(900_000L)
        assertThat(VodSeekPolicy.target(-30L, 0L, false)).isEqualTo(0L)
    }

    @Test fun validDurationBoundsRequestsAndSmallOvershootDoesNotInvalidateIt() {
        assertThat(VodSeekPolicy.contradictsDuration(120_000L, 121_000L)).isFalse()
        assertThat(VodSeekPolicy.target(180_000L, 120_000L, false)).isEqualTo(120_000L)
        assertThat(VodSeekPolicy.target(180_000L, 120_000L, true)).isEqualTo(180_000L)
    }

    @Test fun remoteRepeatUsesTwoMinuteStepsAndSeekRequiresAcknowledgement() {
        assertThat(VodSeekPolicy.remoteStep(0)).isEqualTo(10_000L)
        assertThat(VodSeekPolicy.remoteStep(1)).isEqualTo(120_000L)
        assertThat(VodSeekPolicy.acknowledged(60_000L, 61_000L)).isTrue()
        assertThat(VodSeekPolicy.acknowledged(35_000L, 61_000L)).isFalse()
    }
}
