package app.opentv

import app.opentv.player.VodDecoderFallbackPolicy
import org.junit.Assert.assertFalse
import org.junit.Test

class VodDecoderFallbackPolicyTest {
    @Test fun slowNetworkDoesNotForceVlc() {
        assertFalse(VodDecoderFallbackPolicy.switchOnBufferingTimeout())
    }
}
