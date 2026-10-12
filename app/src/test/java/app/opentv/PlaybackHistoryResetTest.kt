/*
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import androidx.room.Room
import app.opentv.data.db.OpenTvDatabase
import app.opentv.data.model.Episode
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackHistoryResetTest {
    private lateinit var db: OpenTvDatabase

    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), OpenTvDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun close() { db.close() }

    private fun position(profile: Long, key: String, updated: Long = 10) = PlaybackPosition(
        profileId = profile, mediaKey = key,
        positionMillis = 500_000, durationMillis = 1_000_000,
        updatedAtMillis = updated,
    )

    @Test fun resetAllPreservesCredentialsMoviesAndOtherProfiles() = runBlocking {
        val sourceId = db.sources().insert(Source(
            name = "Provider", kind = SourceKind.XTREAM, url = "https://example.org",
            username = "user", password = "pass",
        ))
        db.positions().upsert(position(1, "ep:11"))
        db.positions().upsert(position(1, "ep:12"))
        db.positions().upsert(position(1, "movie:13"))
        db.positions().upsert(position(2, "ep:11"))
        assertThat(db.positions().resetAllEpisodes(1, 100)).isEqualTo(2)
        val reset = db.positions().get(1, "ep:11")!!
        assertThat(reset.positionMillis).isEqualTo(0L)
        assertThat(reset.durationMillis).isEqualTo(0L)
        assertThat(reset.updatedAtMillis).isAtLeast(100L)
        assertThat(db.positions().get(1, "movie:13")!!.positionMillis).isEqualTo(500_000L)
        assertThat(db.positions().get(2, "ep:11")!!.positionMillis).isEqualTo(500_000L)
        assertThat(db.positions().observeRecent(1).first().map { it.mediaKey })
            .containsExactly("movie:13")
        val source = db.sources().byId(sourceId)!!
        assertThat(source.username).isEqualTo("user")
        assertThat(source.password).isEqualTo("pass")
    }

    @Test fun resetSeriesOnlyTargetsOneShow() = runBlocking {
        db.episodes().upsertAll(listOf(
            Episode(id = 11, sourceId = 7, seriesId = "seriesA", episodeId = "A1",
                season = 1, episodeNumber = 1, title = "A1", plot = null,
                durationSeconds = null, stillUrl = null, streamUrl = "https://a/1"),
            Episode(id = 12, sourceId = 7, seriesId = "seriesA", episodeId = "A2",
                season = 2, episodeNumber = 1, title = "A2", plot = null,
                durationSeconds = null, stillUrl = null, streamUrl = "https://a/2"),
            Episode(id = 13, sourceId = 7, seriesId = "seriesB", episodeId = "B1",
                season = 1, episodeNumber = 1, title = "B1", plot = null,
                durationSeconds = null, stillUrl = null, streamUrl = "https://b/1"),
        ))
        for (id in 11L..13L) db.positions().upsert(position(1, "ep:$id"))
        assertThat(db.positions().resetSeriesEpisodes(1, 7, "seriesA", 101)).isEqualTo(2)
        assertThat(db.positions().get(1, "ep:11")!!.positionMillis).isEqualTo(0L)
        assertThat(db.positions().get(1, "ep:12")!!.positionMillis).isEqualTo(0L)
        assertThat(db.positions().get(1, "ep:13")!!.positionMillis).isEqualTo(500_000L)
    }

    @Test fun resetTimestampBeatsPreviousPlaybackTimestamp() = runBlocking {
        db.positions().upsert(position(1, "ep:99", updated = 200))
        db.positions().resetAllEpisodes(1, 100)
        assertThat(db.positions().get(1, "ep:99")!!.updatedAtMillis).isEqualTo(201L)
    }
}
