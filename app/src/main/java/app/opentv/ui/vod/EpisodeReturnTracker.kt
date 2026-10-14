package app.opentv.ui.vod

import app.opentv.data.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process navigation handoff between episode playback and its series page.
 * This does not change the persisted per-profile resume database or store credentials.
 */
internal object EpisodeReturnTracker {
    private val _recent = MutableStateFlow<Map<Pair<Long, String>, Long>>(emptyMap())
    val recent: StateFlow<Map<Pair<Long, String>, Long>> = _recent

    fun record(episode: Episode) {
        _recent.value = _recent.value + ((episode.sourceId to episode.seriesId) to episode.id)
    }
}
