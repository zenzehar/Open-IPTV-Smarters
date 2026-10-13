/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.onboarding

import app.opentv.data.model.SourceKind
import app.opentv.data.repo.SourceRepository

/** A common Xtream mistake: HTTPS on port 80 when the provider serves HTTP there.
 * Merely offer an explicit correction; NEVER downgrade or transmit credentials automatically.
 */
internal fun httpPort80Alternative(url: String, kind: SourceKind, failure: String?): String? {
    if (kind != SourceKind.XTREAM || failure == null) return null
    val lower = failure.lowercase()
    if (!listOf("tls", "ssl", "protocol", "https negotiation", "packet header")
            .any { it in lower }) return null
    val normalized = SourceRepository.normaliseUrl(url, kind)
    if (!normalized.startsWith("https://", ignoreCase = true)) return null
    val parsed = okhttp3.HttpUrl.Companion.toHttpUrlOrNull(normalized) ?: return null
    if (parsed.port != 80) return null
    return parsed.newBuilder().scheme("http").build().toString().trimEnd('/')
}
