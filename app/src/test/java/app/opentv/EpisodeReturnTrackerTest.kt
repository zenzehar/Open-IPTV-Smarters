package app.opentv

import app.opentv.data.model.Episode
import app.opentv.ui.vod.EpisodeReturnTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpisodeReturnTrackerTest {
    @Test fun preservesLastWatchedWithinProfileAndClearsOnlyRequestedSeries() {
        val ep = Episode(
            id = 101L, sourceId = 7L, seriesId = "show7", episodeId = "ep4",
            season = 1, episodeNumber = 4, title = "Episode Four", plot = null,
            durationSeconds = null, stillUrl = null,
            streamUrl = "https://example.test/episode",
        )
        EpisodeReturnTracker.clear(1L, 7L, "show7")
        EpisodeReturnTracker.clear(2L, 7L, "show7")
        EpisodeReturnTracker.record(ep, 1L)
        assertThat(EpisodeReturnTracker.recent.value[Triple(1L, 7L, "show7")]).isEqualTo(101L)
        assertThat(EpisodeReturnTracker.recent.value[Triple(2L, 7L, "show7")]).isNull()
        EpisodeReturnTracker.record(ep.copy(id = 102L), 2L)
        EpisodeReturnTracker.clear(1L, 7L, "show7")
        assertThat(EpisodeReturnTracker.recent.value[Triple(1L, 7L, "show7")]).isNull()
        assertThat(EpisodeReturnTracker.recent.value[Triple(2L, 7L, "show7")]).isEqualTo(102L)
        EpisodeReturnTracker.clear(2L, 7L, "show7")
    }
}
