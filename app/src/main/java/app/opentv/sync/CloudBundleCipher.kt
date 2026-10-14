package app.opentv.sync

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * An encrypted self-contained snapshot for untrusted cloud storage.
 *
 * Bundle JSON includes stream URLs that may carry credentials. Never upload it in plaintext.
 * Version 1: magic (5 bytes), KDF id (1 byte), salt (16 bytes), IV (12 bytes),
 * AES-256-GCM ciphertext + authentication tag. Different devices use the same passphrase.
 */
internal object CloudBundleCipher {
    private val magic = "KSTV1".toByteArray(StandardCharsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val PBKDF2_ITERATIONS = 200_000
    private const val KDF_SHA256: Byte = 1
    private const val KDF_SHA1: Byte = 2
    private val random = SecureRandom()

    fun encrypt(json: String, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Cloud sync passphrase must be at least 12 characters." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val kdfId = if (runCatching {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        }.isSuccess) KDF_SHA256 else KDF_SHA1
        val key = derive(passphrase, salt, kdfId)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            val encrypted = cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8))
            magic + byteArrayOf(kdfId) + salt + nonce + encrypted
        } finally {
            key.encoded?.fill(0)
        }
    }

    fun decrypt(bytes: ByteArray, passphrase: CharArray): String {
        require(bytes.size > magic.size + 1 + SALT_BYTES + NONCE_BYTES + 16) {
            "Invalid encrypted bundle size."
        }
        require(bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Unsupported cloud sync encryption version."
        }
        val algorithm = bytes[magic.size]
        require(algorithm == KDF_SHA256 || algorithm == KDF_SHA1) { "Unknown cloud KDF." }
        val saltStart = magic.size + 1
        val nonceStart = saltStart + SALT_BYTES
        val ciphertextStart = nonceStart + NONCE_BYTES
        val salt = bytes.copyOfRange(saltStart, nonceStart)
        val nonce = bytes.copyOfRange(nonceStart, ciphertextStart)
        val key = derive(passphrase, salt, algorithm)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            String(cipher.doFinal(bytes.copyOfRange(ciphertextStart, bytes.size)),
                StandardCharsets.UTF_8)
        } finally {
            key.encoded?.fill(0)
        }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, algorithm: Byte): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(if (algorithm == KDF_SHA256)
            "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        return try {
            val derived = factory.generateSecret(spec).encoded
            try { SecretKeySpec(derived, "AES") } finally { derived.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
