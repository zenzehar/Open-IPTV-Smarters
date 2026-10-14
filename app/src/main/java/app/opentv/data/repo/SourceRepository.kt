/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.db.SourceDao
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.remote.StalkerApi
import app.opentv.data.remote.XtreamApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SourceRepository(
    private val dao: SourceDao,
    private val api: XtreamApi,
    private val stalkerApi: StalkerApi,
    private val http: OkHttpClient,
) {
    fun observeAll(): Flow<List<Source>> = dao.observeAll()

    suspend fun enabled(): List<Source> = dao.enabled()

    suspend fun byId(id: Long): Source? = dao.byId(id)

    suspend fun save(source: Source): Long = withContext(Dispatchers.IO) {
        val normalised = source.copy(url = normaliseUrl(source.url, source.kind))
        if (source.id == 0L) dao.insert(normalised)
        else {
            dao.update(normalised)
            source.id
        }
    }

    data class ConnectionCheck(val message: String, val expiryMillis: Long? = null)

    suspend fun test(source: Source): Result<String> =
        testDetailed(source).map { it.message }

    /** Checks connection and carries account expiry without exposing login details. */
    suspend fun testDetailed(source: Source): Result<ConnectionCheck> = withContext(Dispatchers.IO) {
        try {
            val checked = source.copy(url = normaliseUrl(source.url, source.kind))
            val check = when (checked.kind) {
                SourceKind.XTREAM -> {
                    val info = api.authenticate(checked)
                    ConnectionCheck(
                        message = buildString {
                            append("Connection verified")
                            info.maxConnections?.let { append(" · $it connection(s)") }
                            info.expiryMillis?.let {
                                append(" · expires ${java.text.DateFormat.getDateInstance().format(java.util.Date(it))}")
                            }
                        },
                        expiryMillis = info.expiryMillis?.takeIf { it > 0L },
                    )
                }
                SourceKind.M3U -> {
                    val address = checked.url.toHttpUrlOrNull()
                        ?: throw IllegalArgumentException("Invalid playlist URL.")
                    val request = Request.Builder().url(address)
                        .header("User-Agent", checked.userAgent).build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful)
                            throw IllegalStateException("Playlist server returned HTTP ${response.code}.")
                        // Reachability only. A full playlist is validated by the streaming M3U
                        // parser during sync, not downloaded twice by Test Connection.
                        ConnectionCheck("Playlist server is reachable. Content will be checked during import.")
                    }
                }
                SourceKind.STALKER -> {
                    stalkerApi.handshakeTest(checked)
                    ConnectionCheck("Portal connection verified. Loading channels…")
                }
            }
            Result.success(check)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Users paste all sorts of things. Accept them all rather than making someone guess
         * the format: a trailing slash, a missing scheme, or a full `get.php` URL copied out
         * of a provider's welcome email.
         *
         * For an Xtream source we want the bare host, so API paths are stripped. For an M3U
         * source the URL *is* the playlist — stripping the path there would destroy it, which
         * is why [kind] is not optional.
         *
         * Pure and static so it can be tested without standing up the repository.
         */
        fun normaliseUrl(raw: String, kind: SourceKind): String {
            var url = raw.trim()
            if (url.isEmpty()) return url
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                url = "http://$url"
            }
            if (kind == SourceKind.M3U) return url
            // A Stalker portal URL is the portal path itself (e.g. .../stalker_portal or .../c) —
            // stripping Xtream API paths would be wrong; just tidy the trailing slash.
            if (kind == SourceKind.STALKER) return url.trimEnd('/')

            url = url.substringBefore("/player_api.php")
                .substringBefore("/panel_api.php")
                .substringBefore("/get.php")
                .substringBefore("/xmltv.php")
            return url.trimEnd('/')
        }
    }
}
