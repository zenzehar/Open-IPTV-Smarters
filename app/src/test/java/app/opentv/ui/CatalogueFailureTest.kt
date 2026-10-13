/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogueFailureTest {
    @Test fun `identifies provider rate limit`() {
        assertThat(safeRefreshFailure("API error HTTP 429 for provider"))
            .contains("HTTP 429")
    }

    @Test fun `identifies authentication failures`() {
        assertThat(safeRefreshFailure("Username or password rejected by the server."))
            .contains("rejected the account")
    }

    @Test fun `never renders provider URL or credentials`() {
        val privateUrl = "http://private.example/player_api.php?username=secret_user&password=secret_password"
        val message = safeRefreshFailure("\"$privateUrl\" is not a valid server address.")
        assertThat(message).contains("Invalid server URL")
        assertThat(message).doesNotContain("private.example")
        assertThat(message).doesNotContain("secret_user")
        assertThat(message).doesNotContain("secret_password")
    }

    @Test fun `sanitizes unexpected server messages`() {
        assertThat(safeRefreshFailure("Error from server: token=secret123")).doesNotContain("secret123")
    }

    @Test fun `recognizes timeouts`() {
        assertThat(safeRefreshFailure("java.net.SocketTimeoutException: timed out"))
            .contains("timed out")
    }
}
