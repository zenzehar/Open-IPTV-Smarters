/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.onboarding

import app.opentv.data.model.SourceKind
import app.opentv.ui.safeRefreshFailure
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConnectionGuidanceTest {
    @Test fun `TLS packet error produces actionable safe diagnosis`() {
        val message = safeRefreshFailure("Unable to parse TLS packet header")
        assertThat(message).contains("HTTPS")
        assertThat(message).contains("HTTP")
    }

    @Test fun `offers HTTP only on failing HTTPS port 80 Xtream source`() {
        val failure = safeRefreshFailure("Unable to parse TLS packet header")
        assertThat(httpPort80Alternative("https://example.org:80", SourceKind.XTREAM, failure))
            .isEqualTo("http://example.org")
    }

    @Test fun `never changes already HTTP or other port`() {
        val fail = "HTTPS negotiation failed"
        assertThat(httpPort80Alternative("http://example.org:80", SourceKind.XTREAM, fail)).isNull()
        assertThat(httpPort80Alternative("https://example.org:443", SourceKind.XTREAM, fail)).isNull()
        assertThat(httpPort80Alternative("https://example.org:80", SourceKind.XTREAM, null)).isNull()
        assertThat(httpPort80Alternative("https://example.org:80", SourceKind.M3U, fail)).isNull()
    }

    @Test fun `does not suggest HTTP for wrong password or temporary timeout`() {
        assertThat(httpPort80Alternative("https://example.org:80", SourceKind.XTREAM,
            "Provider rejected the account")).isNull()
        assertThat(httpPort80Alternative("https://example.org:80", SourceKind.XTREAM,
            "Provider request timed out")).isNull()
    }

    @Test fun `line separator parsing never merges different credentials`() {
        assertThat(oneLine("alice\\nbob")).isEqualTo("alice\\nbob")
        assertThat(oneLine("alice\nbob")).isEqualTo("alice")
        assertThat(oneLine("alice\rbob")).isEqualTo("alice")
        assertThat(oneLine("alice\u2028bob")).isEqualTo("alice")
        assertThat(oneLine("alice\u2029bob")).isEqualTo("alice")
    }
}
