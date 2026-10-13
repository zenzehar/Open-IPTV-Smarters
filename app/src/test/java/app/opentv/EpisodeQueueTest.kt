/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors.
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Episode
import app.opentv.ui.vod.findNextEpisode
import app.opentv.ui.vod.canAutoplayAfterCompletion
import app.opentv.ui.vod.mostRecentPerTitle
import app.opentv.ui.onboarding.oneLine
import com.google.common.truth.Truth.assertThat
import app.opentv.player.PlaybackErrors
import androidx.media3.common.PlaybackException
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
    @Test fun stoppedOrFailedPlaybackNeverAutoplays() {
        val next = episode(20, 1, 2)
        assertThat(canAutoplayAfterCompletion(false, true, true, next)).isFalse()
        assertThat(canAutoplayAfterCompletion(false, false, true, next)).isFalse()
    }

    @Test fun episodeProgressMustBeHandledBeforeAutoplay() {
        assertThat(canAutoplayAfterCompletion(true, false, true, episode(20, 1, 2))).isFalse()
        assertThat(canAutoplayAfterCompletion(true, true, true, episode(20, 1, 2))).isTrue()
    }

    @Test fun disablingAutoplayOrEndingSeriesBlocksAutomaticNext() {
        assertThat(canAutoplayAfterCompletion(true, true, false, episode(20, 1, 2))).isFalse()
        assertThat(canAutoplayAfterCompletion(true, true, true, null)).isFalse()
    }

    private data class HistoryCard(val group: String, val mediaKey: String, val updatedAt: Long)

    @Test fun oneSeriesCardAlwaysPointsToLatestStoppedEpisode() {
        val history = listOf(
            HistoryCard("series:4:show-a", "ep:1", 100L),
            HistoryCard("series:4:show-b", "ep:3", 130L),
            HistoryCard("series:4:show-a", "ep:2", 200L),
        )
        val latest = mostRecentPerTitle(history, { it.updatedAt }, { it.group })
        assertThat(latest.map { it.mediaKey }).containsExactly("ep:2", "ep:3").inOrder()
    }

    @Test fun similarlyNamedSeriesFromDifferentProvidersRemainSeparate() {
        val history = listOf(
            HistoryCard("series:4:show-a", "ep:1", 100L),
            HistoryCard("series:5:show-a", "ep:2", 200L),
        )
        val latest = mostRecentPerTitle(history, { it.updatedAt }, { it.group })
        assertThat(latest).hasSize(2)
    }

    @Test fun duplicateMovieQualitiesKeepMostRecentlyWatchedOne() {
        val history = listOf(
            HistoryCard("movie:arrival:2016", "movie:old", 100L),
            HistoryCard("movie:arrival:2016", "movie:new", 210L),
            HistoryCard("movie:arrival:2023", "movie:other", 190L),
        )
        val latest = mostRecentPerTitle(history, { it.updatedAt }, { it.group })
        assertThat(latest.map { it.mediaKey }).containsExactly("movie:new", "movie:other").inOrder()
    }

    @Test fun unsupportedMedia3FormatsSkipPointlessRetriesBeforeVlc() {
        assertThat(PlaybackErrors.requiresAlternativeDecoder(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        )).isTrue()
        assertThat(PlaybackErrors.requiresAlternativeDecoder(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )).isTrue()
        assertThat(PlaybackErrors.requiresAlternativeDecoder(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )).isFalse()
    }

    @Test fun pastedMultilineCredentialsNeverBecomeOneInput() {
        assertThat(oneLine("username\npassword")).isEqualTo("username")
        assertThat(oneLine("url\r\nextra")).isEqualTo("url")
        assertThat(oneLine("playlist name")).isEqualTo("playlist name")
        assertThat(oneLine("abc\u2028def")).isEqualTo("abc")
    }

}
