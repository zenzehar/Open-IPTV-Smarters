package app.opentv

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opentv.player.VodSeekPolicy
import app.opentv.sync.CloudCredentialVault
import app.opentv.sync.CloudCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic smoke tests on Android phone and TV emulators. These check process startup,
 * Android UI lifecycle, resume policy and AndroidKeyStore. No IPTV credentials are needed.
 * Decoder-specific stream tests still require actual supported provider recordings.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @Test fun startsMainActivityOnActualAndroidRuntime() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("MainActivity unexpectedly finishing", activity.isFinishing)
                assertNotNull(activity.window)
            }
        }
    }

    @Test fun nativeTimePolicyPreventsPrematureCompletion() {
        val actual = 44L * 60_000L + 14_000L
        val reported = 44L * 60_000L
        assertTrue(VodSeekPolicy.contradictsDuration(reported, actual))
        assertEquals(0L, VodSeekPolicy.safeDuration(reported, actual, false))
        assertEquals(actual + 120_000L,
            VodSeekPolicy.relative(actual, 120_000L, reported, true))
    }

    @Test fun androidKeystoreKeepsCloudSecretsOutOfPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = CloudCredentialVault(context)
        val secret = CloudCredentials(
            "https://cloud.example.test", "test-user", "synthetic-password",
            "long-synthetic-family-passphrase",
        )
        try {
            vault.save(secret)
            val saved = vault.load()
            assertNotNull(saved)
            assertEquals(secret.server, saved?.server)
            assertEquals(secret.username, saved?.username)
            assertEquals(secret.appPassword, saved?.appPassword)
            assertEquals(secret.syncPassphrase, saved?.syncPassphrase)
            val stored = context.getSharedPreferences("cloud_relay_v1", 0)
                .getString("encrypted", "").orEmpty()
            assertFalse(stored.contains("synthetic-password"))
            assertFalse(stored.contains("test-user"))
        } finally {
            vault.clear()
        }
    }
}
