package app.opentv.ui.vod

import app.opentv.data.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transient TV episode highlight and navigation handoff. Must be keyed by profile,
 * otherwise switching profiles leaks one person's watch selection into another.
 */
internal object EpisodeReturnTracker {
    private val _recent = MutableStateFlow<Map<Triple<Long, Long, String>, Long>>(emptyMap())
    val recent: StateFlow<Map<Triple<Long, Long, String>, Long>> = _recent

    fun record(episode: Episode, profileId: Long) {
        _recent.value = _recent.value +
            (Triple(profileId, episode.sourceId, episode.seriesId) to episode.id)
    }

    fun clear(profileId: Long, sourceId: Long, seriesId: String) {
        _recent.value = _recent.value - Triple(profileId, sourceId, seriesId)
    }
}
