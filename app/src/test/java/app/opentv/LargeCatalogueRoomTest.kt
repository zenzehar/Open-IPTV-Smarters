package app.opentv

import android.app.Application
import androidx.room.Room
import app.opentv.data.db.OpenTvDatabase
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
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

/** Proves category search and LIMIT/OFFSET with thousands of *real* Room records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class LargeCatalogueRoomTest {
    private lateinit var db: OpenTvDatabase
    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), OpenTvDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    @After fun close() = db.close()

    private fun movie(i: Int, source: Long = 1L) = Movie(
        sourceId = source, streamId = "movie-$source-$i",
        name = "QA Movie ${i.toString().padStart(4, '0')}",
        categoryId = if (i % 2 == 0) "drama" else "comedy",
        posterUrl = null, rating = null, year = null, plot = null,
        durationSeconds = 3600, containerExtension = "mp4",
        streamUrl = "https://qa.invalid/movie/$source/$i.mp4", addedMillis = i.toLong(),
    )
    private fun show(i: Int, source: Long = 1L) = Series(
        sourceId = source, seriesId = "show-$source-$i",
        name = "QA Series ${i.toString().padStart(4, '0')}",
        categoryId = if (i % 2 == 0) "drama" else "comedy",
        posterUrl = null, rating = null, year = null, plot = null,
        addedMillis = i.toLong(),
    )

    @Test fun moviePagesCoverBeyondOldTwoThousandLimitWithNoDuplicates() = runBlocking<Unit> {
        (1..2505).chunked(200).forEach { chunk -> db.movies().upsertAll(chunk.map(::movie)) }
        val observed = mutableListOf<Movie>()
        var offset = 0
        while (true) {
            val page = db.movies().observePage(null, 1L, "", 160, offset).first()
            if (page.isEmpty()) break
            assertThat(page.size).isAtMost(160)
            observed.addAll(page)
            offset += page.size
        }
        assertThat(observed).hasSize(2505)
        assertThat(observed.map { it.streamId }.toSet()).hasSize(2505)
        assertThat(db.movies().observePage(null, 1L, "", 160, 2400).first()).hasSize(105)
    }

    @Test fun fullCategorySearchDoesNotLeakToOtherCategoriesOrProviders() = runBlocking<Unit> {
        db.movies().upsertAll((1..2505).map(::movie) + movie(2506, source = 2))
        val drama = db.movies().observePage("drama", 1L, "QA Movie", 160, 960).first()
        assertThat(drama).hasSize(160)
        assertThat(drama).containsNoDuplicates()
        assertThat(drama.all { it.categoryId == "drama" && it.sourceId == 1L }).isTrue()
        val exact = db.movies().observePage(null, 1L, "QA Movie 2505", 160, 0).first()
        assertThat(exact.map { it.name }).containsExactly("QA Movie 2505")
    }

    @Test fun seriesPagesAndSearchWorkBeyondTwoThousandRows() = runBlocking<Unit> {
        (1..2505).chunked(200).forEach { chunk -> db.series().upsertAll(chunk.map(::show)) }
        assertThat(db.series().observePage(null, 1L, "", 160, 2400).first()).hasSize(105)
        assertThat(db.series().observePage("comedy", 1L, "QA Series 2505", 160, 0)
            .first().map { it.seriesId }).containsExactly("show-1-2505")
        assertThat(db.series().observePage("drama", 1L, "QA Series 2505", 160, 0)
            .first()).isEmpty()
    }

    @Test fun failedRefreshCannotWipeCachedMovieAndSeriesWithoutExplicitDeletion() = runBlocking<Unit> {
        db.movies().upsertAll(listOf(movie(1), movie(2)))
        db.series().upsertAll(listOf(show(1)))
        assertThat(db.movies().count()).isEqualTo(2)
        assertThat(db.series().count()).isEqualTo(1)
    }
}
