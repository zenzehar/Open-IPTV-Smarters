/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors.
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Episode
import app.opentv.ui.vod.findNextEpisode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpisodeQueueTest {
    private fun episode(id: Long, season: Int, number: Int) = Episode(
        id = id, sourceId = 1, seriesId = "show-a", episodeId = "episode-$id",
        season = season, episodeNumber = number,
        title = "Episode $number", plot = null, durationSeconds = null,
        stillUrl = null, streamUrl = "https://example.org/episode/$id",
    )

    @Test fun advancesByEpisodeNumberNotProviderOrder() {
        val unordered = listOf(episode(30, 1, 3), episode(10, 1, 1), episode(20, 1, 2))
        assertThat(findNextEpisode(unordered, 10)?.id).isEqualTo(20L)
    }

    @Test fun crossesIntoNextSeason() {
        val unordered = listOf(episode(30, 2, 1), episode(10, 1, 1), episode(20, 1, 9))
        assertThat(findNextEpisode(unordered, 20)?.id).isEqualTo(30L)
    }

    @Test fun endOfSeriesDoesNotAutoAdvance() {
        val list = listOf(episode(10, 1, 1), episode(20, 2, 9))
        assertThat(findNextEpisode(list, 20)).isNull()
    }

    @Test fun missingCurrentEpisodeDoesNotJumpToStart() {
        assertThat(findNextEpisode(listOf(episode(10, 1, 1)), 99L)).isNull()
    }
}
