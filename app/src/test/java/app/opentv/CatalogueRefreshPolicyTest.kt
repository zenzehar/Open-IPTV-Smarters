package app.opentv

import app.opentv.data.work.CatalogueRefreshPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueRefreshPolicyTest {
    @Test fun offDisablesRefresh() {
        assertFalse(CatalogueRefreshPolicy.isDue(0, 0, 999))
    }
    @Test fun firstLaunchNeedsUpdate() {
        assertTrue(CatalogueRefreshPolicy.isDue(0, 6, 999))
    }
    @Test fun selectedIntervalIsRespected() {
        val now = 50L * 3_600_000L
        assertFalse(CatalogueRefreshPolicy.isDue(now - 5L * 3_600_000L, 6, now))
        assertTrue(CatalogueRefreshPolicy.isDue(now - 6L * 3_600_000L, 6, now))
        assertFalse(CatalogueRefreshPolicy.isDue(now - 6L * 3_600_000L, 12, now))
    }
    @Test fun futureStampNeverRedownloads() {
        assertFalse(CatalogueRefreshPolicy.isDue(2000, 1, 1000))
    }
}
