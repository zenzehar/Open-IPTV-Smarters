package app.opentv.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Secrets are used only for encrypted sync and never printed to logs or placed in a QR code. */
internal class CloudCredentials(
    val server: String,
    val username: String,
    val appPassword: String,
    val syncPassphrase: String,
) {
    override fun toString(): String = "CloudCredentials(redacted)"
}

/**
 * A per-install Android Keystore-backed vault. Cloud snapshots have a separate cross-device
 * passphrase because the Android Keystore key cannot travel to another phone or TV.
 */
internal class CloudCredentialVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cloud_relay_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): CloudCredentials? = runCatching {
        val stored = prefs.getString("encrypted", null) ?: return null
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        require(blob.size >= 13)
        val key = key()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        val obj = json.parseToJsonElement(String(
            cipher.doFinal(blob.copyOfRange(12, blob.size)),
            StandardCharsets.UTF_8,
        )).jsonObject
        CloudCredentials(
            server = obj.getValue("server").jsonPrimitive.content,
            username = obj.getValue("user").jsonPrimitive.content,
            appPassword = obj.getValue("appPassword").jsonPrimitive.content,
            syncPassphrase = obj.getValue("passphrase").jsonPrimitive.content,
        )
    }.getOrNull()

    fun save(credentials: CloudCredentials) {
        require(credentials.syncPassphrase.length >= 12)
        val plaintext = buildJsonObject {
            put("server", credentials.server)
            put("user", credentials.username)
            put("appPassword", credentials.appPassword)
            put("passphrase", credentials.syncPassphrase)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // Android Keystore requires randomized encryption: NEVER supply our own IV.
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            require(iv.size == 12) { "Unsupported Keystore GCM nonce size." }
            val encrypted = iv + cipher.doFinal(plaintext)
            prefs.edit().putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
        } finally {
            plaintext.fill(0)
        }
    }

    fun clear() {
        // Remove only cloud relay credentials. Existing NAS and manual LAN settings remain intact.
        prefs.edit().remove("encrypted").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "koky_sosy_cloud_v1"
    }
}
