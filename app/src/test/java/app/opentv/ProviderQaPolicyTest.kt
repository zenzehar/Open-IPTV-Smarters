package app.opentv

import app.opentv.data.work.ProviderQaPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderQaPolicyTest {
    @Test fun freshInstallRunsCheckAutomatically() {
        assertTrue(ProviderQaPolicy.isDue(0L, 1000L))
    }
    @Test fun checksDoNotRepeatOnEveryAppLaunch() {
        assertFalse(ProviderQaPolicy.isDue(1_000_000L, 1_060_000L))
    }
    @Test fun checksRepeatAfterOneDay() {
        assertTrue(ProviderQaPolicy.isDue(1_000L, 1_000L + ProviderQaPolicy.INTERVAL_MS))
    }
    @Test fun clockRollbackRechecks() {
        assertTrue(ProviderQaPolicy.isDue(9_000L, 8_000L))
    }
}
