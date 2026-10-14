package app.opentv

import app.opentv.sync.CloudBundleCipher
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudBundleCipherTest {
    @Test fun syncSnapshotRoundTripsAndUsesUniqueNonce() {
        val pass = "correct-stable-family-password".toCharArray()
        val content = "{\"positions\":[{\"streamUrl\":\"https://user:secret@example.test/vod\"}]}"
        val a = CloudBundleCipher.encrypt(content, pass)
        val b = CloudBundleCipher.encrypt(content, pass)
        assertThat(a.contentEquals(b)).isFalse()
        assertThat(String(a)).doesNotContain("user:secret")
        assertThat(CloudBundleCipher.decrypt(a, pass)).isEqualTo(content)
        assertThat(CloudBundleCipher.decrypt(b, pass)).isEqualTo(content)
    }

    @Test fun wrongPassphraseAndCorruptionNeverLeakContent() {
        val data = CloudBundleCipher.encrypt("{\"secret\":\"test\"}", "some-long-secret-phrase".toCharArray())
        val corrupted = data.copyOf()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        assertThat(runCatching {
            CloudBundleCipher.decrypt(data, "different-long-secret".toCharArray())
        }.isFailure).isTrue()
        assertThat(runCatching {
            CloudBundleCipher.decrypt(corrupted, "some-long-secret-phrase".toCharArray())
        }.isFailure).isTrue()
    }

    @Test fun tooShortSharedPassphraseIsRejected() {
        assertThat(runCatching {
            CloudBundleCipher.encrypt("test", "short".toCharArray())
        }.isFailure).isTrue()
    }
}
