package app.opentv.sync

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Official Nextcloud Login Flow v2. The TV shows the HTTPS [Pending.loginUrl] as a QR
 * code; the phone authorises in its browser; polling returns a revocable app password.
 * No client secret, cloud password, or IPTV token is embedded in the QR or repository.
 */
internal class NextcloudLogin {
    data class Pending(
        val server: String,
        val token: String,
        val endpoint: String,
        val loginUrl: String,
    )
    data class Granted(
        val server: String,
        val username: String,
        val appPassword: String,
    ) {
        override fun toString(): String = "Granted(redacted)"
    }

    private val client = OkHttpClient.Builder().followRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun begin(serverInput: String): Pending = withContext(Dispatchers.IO) {
        val root = normalizedServer(serverInput)
        val loginEndpoint = root.toString().trimEnd('/') + "/index.php/login/v2"
        val req = Request.Builder().url(loginEndpoint)
            .post(FormBody.Builder().build()).build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Cloud login unavailable (HTTP ${response.code}).")
            val obj = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val poll = obj.getValue("poll").jsonObject
            val endpoint = poll.getValue("endpoint").jsonPrimitive.content
            val loginUrl = obj.getValue("login").jsonPrimitive.content
            requireSameOrigin(root, endpoint)
            requireSameOrigin(root, loginUrl)
            Pending(
                server = root.toString().trimEnd('/'),
                token = poll.getValue("token").jsonPrimitive.content,
                endpoint = endpoint,
                loginUrl = loginUrl,
            )
        }
    }

    suspend fun poll(pending: Pending): Granted? = withContext(Dispatchers.IO) {
        requireSameOrigin(normalizedServer(pending.server), pending.endpoint)
        val request = Request.Builder().url(pending.endpoint)
            .post(FormBody.Builder().add("token", pending.token).build()).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("Cloud authorisation error (HTTP ${response.code}).")
            val obj = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val server = normalizedServer(obj.getValue("server").jsonPrimitive.content)
            requireSameOrigin(normalizedServer(pending.server), server.toString())
            Granted(server.toString().trimEnd('/'),
                obj.getValue("loginName").jsonPrimitive.content,
                obj.getValue("appPassword").jsonPrimitive.content)
        }
    }

    private fun normalizedServer(serverInput: String): HttpUrl {
        val url = serverInput.trim().trimEnd('/').toHttpUrl()
        require(url.scheme == "https" && url.username.isBlank() && url.password.isBlank() &&
            url.query == null && url.fragment == null && url.host.isNotBlank()) {
            "Use a secure HTTPS Nextcloud server URL without credentials."
        }
        return url
    }

    private fun requireSameOrigin(root: HttpUrl, candidate: String) {
        val endpoint = candidate.toHttpUrl()
        require(endpoint.scheme == "https" && endpoint.host == root.host &&
            endpoint.port == root.port && endpoint.username.isBlank() && endpoint.password.isBlank()) {
            "Cloud login redirected to an unexpected origin."
        }
    }
}
