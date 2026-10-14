package app.opentv.sync

import android.content.Context
import android.net.Uri
import android.util.Xml
import app.opentv.core.ServiceLocator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser

/**
 * An always-available cloud rendezvous for independent Android installations.
 * Uses Nextcloud WebDAV, one AES-GCM bundle per stable device UUID. Different devices need
 * not be simultaneously online. The user's existing manual LAN/NAS sync is untouched.
 */
internal class CloudRelaySync(
    private val context: Context,
    private val graph: ServiceLocator.Graph,
) {
    sealed interface Result {
        data class Success(val merged: Int, val peerDevices: Int) : Result
        data object NotConfigured : Result
        data class Failed(val reason: String) : Result
    }

    private val engine = SyncEngine(graph)
    private val http = OkHttpClient.Builder().followRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sync(): Result = withContext(Dispatchers.IO) {
        val credentials = CloudCredentialVault(context).load()
            ?: return@withContext Result.NotConfigured
        runCatching<Result> {
            val root = davRoot(credentials)
            val folder = root + "KokySosyTV/"
            val syncFolder = folder + "sync/"
            val auth = Credentials.basic(credentials.username, credentials.appPassword)
            ensureFolder(folder, auth)
            ensureFolder(syncFolder, auth)
            val deviceId = graph.settings.syncDeviceId
            val ownName = "$deviceId.kst"
            val password = credentials.syncPassphrase.toCharArray()
            try {
                // 1. Publish immediately: even if this device goes offline, others can catch up.
                publish(syncFolder + ownName, auth,
                    CloudBundleCipher.encrypt(engine.encode(engine.gather()), password))

                // 2. Merge each available peer. Individual malformed/tampered files are ignored.
                val peers = discover(syncFolder, auth)
                    .filter { it != ownName }
                    .take(MAX_PEERS)
                var merged = SyncEngine.MergeResult()
                var validPeers = 0
                for (name in peers) {
                    val bytes = runCatching { download(syncFolder + name, auth) }
                        .getOrNull() ?: continue
                    val snapshot = runCatching {
                        engine.decode(CloudBundleCipher.decrypt(bytes, password))
                    }.getOrNull() ?: continue
                    merged += engine.apply(snapshot)
                    validPeers++
                }
                // 3. Republish converged local state to avoid waiting for another periodic run.
                if (validPeers > 0) publish(syncFolder + ownName, auth,
                    CloudBundleCipher.encrypt(engine.encode(engine.gather()), password))
                Result.Success(merged.total, validPeers)
            } finally {
                password.fill('\u0000')
            }
        }.getOrElse {
            // Deliberately suppress exception messages: endpoints or URLs could carry private data.
            Result.Failed("Cloud relay unavailable. Check HTTPS login, server access and sync passphrase.")
        }
    }

    private fun davRoot(config: CloudCredentials): String {
        val base = config.server.trimEnd('/')
        require(base.startsWith("https://", ignoreCase = true))
        return "$base/remote.php/dav/files/${Uri.encode(config.username)}/"
    }

    private fun request(url: String, auth: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", auth)
            .header("Cache-Control", "no-store")

    private fun ensureFolder(url: String, auth: String) {
        http.newCall(request(url, auth).method("MKCOL", null).build()).execute().use {
            if (!it.isSuccessful && it.code != 405) throw IOException("MKCOL failed")
        }
    }

    private fun publish(url: String, auth: String, ciphertext: ByteArray) {
        require(ciphertext.size <= MAX_BUNDLE_BYTES)
        http.newCall(request(url, auth)
            .put(ciphertext.toRequestBody("application/octet-stream".toMediaType()))
            .build()).execute().use { if (!it.isSuccessful) throw IOException("PUT failed") }
    }

    private fun download(url: String, auth: String): ByteArray {
        http.newCall(request(url, auth).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GET failed")
            if ((response.body?.contentLength() ?: 0L) > MAX_BUNDLE_BYTES) {
                throw IOException("Snapshot exceeds size limit")
            }
            val input = response.body?.byteStream() ?: throw IOException("Empty response")
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            var size = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                size += n
                if (size > MAX_BUNDLE_BYTES) throw IOException("Snapshot exceeds size limit")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun discover(syncFolder: String, auth: String): List<String> {
        val body = """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>
        """.trimIndent()
        val reply = http.newCall(request(syncFolder, auth)
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .build()).execute().use { response ->
                if (response.code != 207) throw IOException("PROPFIND failed")
                response.body?.string()?.takeIf { it.length <= MAX_XML_CHARS }
                    ?: throw IOException("Invalid listing")
        }
        val parser = Xml.newPullParser()
        parser.setInput(reply.reader())
        val names = LinkedHashSet<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("href", true)) {
                val text = parser.nextText()
                val name = Uri.parse(text).lastPathSegment ?: ""
                if (FILE_NAME.matches(name)) names.add(name)
            }
            event = parser.next()
        }
        return names.toList().sorted()
    }

    private companion object {
        const val MAX_BUNDLE_BYTES = 4 * 1024 * 1024
        const val MAX_XML_CHARS = 300_000
        const val MAX_PEERS = 40
        val FILE_NAME = Regex("[a-fA-F0-9-]{36}\\.kst")
    }
}
