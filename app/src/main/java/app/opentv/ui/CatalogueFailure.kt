/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

/**
 * Provider exceptions can contain full request URLs with Xtream credentials.
 * Only publish allow-listed diagnostic categories in user-visible refresh status.
 * This also avoids exposing provider-returned text directly in the dashboard.
 */
internal fun safeRefreshFailure(raw: String?): String {
    val reason = raw.orEmpty()
    val httpCode = Regex("""(?i)\bHTTP\s*(\d{3})\b""").find(reason)
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
    val description = reason.lowercase()
    return when {
        httpCode == 401 || httpCode == 403 ->
            "Provider denied access (HTTP $httpCode). Verify your subscription or login."
        httpCode == 404 || httpCode == 405 ->
            "Provider API unavailable (HTTP $httpCode). Check the server URL and port."
        httpCode == 429 ->
            "Provider is limiting requests (HTTP 429). Wait before retrying."
        httpCode != null && httpCode in 500..599 ->
            "Provider server error (HTTP $httpCode). Try again later."
        httpCode != null ->
            "Provider request failed (HTTP $httpCode)."
        "timed out" in description || "timeout" in description ->
            "Provider request timed out. Try again in a moment."
        "unknownhost" in description || "unable to resolve host" in description ||
            "no address associated with hostname" in description ->
            "Server hostname cannot be resolved. Check the address and network."
        "ssl" in description || "certificate" in description || "handshake" in description ->
            "Secure connection failed. Check the server and device clock."
        "username or password" in description || "account is" in description ||
            "not authorised" in description || "not authorized" in description ->
            "Provider rejected the account. Verify login and subscription."
        "not valid server address" in description || "invalid url" in description ||
            "invalid server" in description ->
            "Invalid server URL. Check the provider address and port."
        "no channels" in description || "empty catalogue" in description ->
            "The provider returned no channels for this account."
        "connect" in description || "network" in description ||
            "socket" in description || "ioexception" in description ->
            "Network connection failed. Check connectivity and retry."
        else -> "Catalogue update failed. Check your provider connection and retry."
    }
}
