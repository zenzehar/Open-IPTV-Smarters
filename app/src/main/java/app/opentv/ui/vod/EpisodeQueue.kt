/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors.
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import app.opentv.data.model.Episode

/**
 * Advance only to the next episode in the same provider's series.
 * Season and episode numbers, rather than provider insertion order, determine sequence.
 */
internal fun findNextEpisode(episodes: List<Episode>, currentId: Long): Episode? {
    val sorted = episodes.sortedWith(
        compareBy<Episode> { it.season }.thenBy { it.episodeNumber }.thenBy { it.id }
    )
    val index = sorted.indexOfFirst { it.id == currentId }
    return if (index >= 0) sorted.getOrNull(index + 1) else null
}

/**
 * Media3 has a separate idle state for stopped or failed playback. Only a confirmed
 * end event, after final watch progress is handled, may trigger automatic progression.
 */
internal fun canAutoplayAfterCompletion(
    reachedEnd: Boolean,
    completionHandled: Boolean,
    autoplayEnabled: Boolean,
    nextEpisode: Episode?,
): Boolean = reachedEnd && completionHandled && autoplayEnabled && nextEpisode != null
