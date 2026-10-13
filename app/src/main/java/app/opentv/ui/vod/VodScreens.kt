/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.opentv.ui.explicitImeActivation
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import app.opentv.core.AppSettings
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.parser.displayTitle
import app.opentv.ui.VodViewModel
import app.opentv.ui.VodLoadPhase
import coil.compose.AsyncImage

/**
 * Movies: a modern, row-based home — Continue Watching, Recommended, Recently added and a row per
 * genre, each a horizontally-scrolling shelf of poster cards. A category chip strip along the top
 * keeps whole-category browsing one press away without a permanent rail eating the width. Clicking
 * a film opens its detail page rather than playing straight away, the streaming-app convention.
 */
@Composable
fun MoviesScreen(
    onOpenMovie: (Movie) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenResumeDetails: (VodViewModel.ResumeItem) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    isTelevision: Boolean = false,
    categoryJumpFocus: FocusRequester? = null,
    titleFilter: String = "",
    onTitleFilterChange: (String) -> Unit = {},
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.movieCategories.collectAsState()
    val resume by viewModel.continueWatchingMovies.collectAsState()
    val recommended by viewModel.recommendedMovies.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedMovies.collectAsState()
    val genreRows by viewModel.movieGenreRows.collectAsState()
    // Category grid uses a bounded Room page instead of subscribing to the full category.
    val allMovies by viewModel.allMovies.collectAsState()
    val loadState by viewModel.movieStatus.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedSource by viewModel.selectedVodSource.collectAsState()

    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }
    var browseCategory by remember { mutableStateOf<String?>(RECENTLY_ADDED_CATEGORY) }
    val matchTitle: (Movie) -> Boolean = { movie ->
        movie.displayTitle.contains(titleFilter.trim(), ignoreCase = true) &&
            (selectedSource == null || movie.sourceId == selectedSource)
    }
    val filteredResume = resume.filter { it.title.contains(titleFilter.trim(), ignoreCase = true) }
    val filteredRecommended = recommended.filter(matchTitle)
    val filteredRecent = recentlyAdded.filter(matchTitle)
    val filteredGenreRows = genreRows.mapNotNull { row ->
        val items = row.items.filter(matchTitle)
        if (items.isEmpty()) null else row.copy(items = items)
    }
    val hasContent = resume.isNotEmpty() || recommended.isNotEmpty() ||
        recentlyAdded.isNotEmpty() || genreRows.isNotEmpty()
    val onCategory: (String) -> Unit = { id ->
        // Keep the active query while switching categories: re-filter in place.
        browseCategory = id
        viewModel.selectMovieCategory(if (isSpecialVodCategory(id)) null else id)
    }
    val onAll: () -> Unit = {
        browseCategory = null
        viewModel.selectMovieCategory(null)
    }

    val scopedMovies = remember(allMovies, selectedSource) {
        if (selectedSource == null) allMovies else allMovies.filter { it.sourceId == selectedSource }
    }
    val counts = remember(scopedMovies, resume, recentlyAdded) {
        scopedMovies.mapNotNull { it.categoryId }.groupingBy { it }.eachCount() +
            mapOf(
                ALL_CATEGORY to scopedMovies.size,
                FAVOURITES_CATEGORY to scopedMovies.count { it.favourite },
                CONTINUE_WATCHING_CATEGORY to resume.size,
                RECENTLY_ADDED_CATEGORY to recentlyAdded.size,
            )
    }
    val entries = (vodSections() + categories.map { it.id to it.name }).distinctBy { it.first }
    Column(Modifier.fillMaxSize().background(Color(0xFF0C1526))) {
        if (sources.size > 1) ProviderChips(
            sources = sources,
            selected = selectedSource,
            onSelectAll = { browseCategory = RECENTLY_ADDED_CATEGORY; viewModel.selectVodSource(null) },
            onSelectSource = { id -> browseCategory = RECENTLY_ADDED_CATEGORY; viewModel.selectVodSource(id) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VodBrowserFrame(
                entries = entries,
                counts = counts,
                selected = browseCategory,
                isTelevision = isTelevision,
                onSelectHome = onAll,
                onSelectCategory = onCategory,
                categoryJumpFocus = categoryJumpFocus,
            ) {
                Box(Modifier.fillMaxSize()) {
                when {
                    (loadState.phase == VodLoadPhase.PENDING || loadState.phase == VodLoadPhase.LOADING) &&
                        allMovies.isEmpty() ->
                        LoadingVod(loadState.message.ifBlank { "Loading movies…" })
                    loadState.phase == VodLoadPhase.FAILED && allMovies.isEmpty() ->
                        VodLoadFailure("Movies are unavailable", loadState.message, viewModel::refreshVod)
                    loadState.phase == VodLoadPhase.READY && allMovies.isEmpty() && browseCategory == null ->
                        LoadingVod("Preparing movie catalogue…")
                    browseCategory == CONTINUE_WATCHING_CATEGORY -> ContinueWatchingGrid(resume.filter {
                        it.title.contains(titleFilter.trim(), ignoreCase = true) }, onResume, onOpenResumeDetails, isTelevision, viewModel)
                    browseCategory == FAVOURITES_CATEGORY -> MovieCategoryGrid(scopedMovies.filter {
                        it.favourite && it.displayTitle.contains(titleFilter.trim(), ignoreCase = true) },
                        viewModel, onOpenMovie)
                    titleFilter.isNotBlank() && browseCategory == RECENTLY_ADDED_CATEGORY ->
                        PagedMovieCategoryGrid(null, selectedSource, titleFilter, viewModel, onOpenMovie)
                    browseCategory == RECENTLY_ADDED_CATEGORY -> MovieCategoryGrid(recentlyAdded.filter {
                        (selectedSource == null || it.sourceId == selectedSource) &&
                            it.displayTitle.contains(titleFilter.trim(), ignoreCase = true)
                    }, viewModel, onOpenMovie)
                    browseCategory != null -> PagedMovieCategoryGrid(
                        categoryId = browseCategory!!,
                        sourceId = selectedSource,
                        filter = titleFilter,
                        viewModel = viewModel,
                        onOpenMovie = onOpenMovie,
                    )
                    titleFilter.isNotBlank() && browseCategory == null ->
                        PagedMovieCategoryGrid(null, selectedSource, titleFilter, viewModel, onOpenMovie)
                    !hasContent -> when {
                        loadState.phase == VodLoadPhase.LOADING || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_movies))
                        scopedMovies.isNotEmpty() -> MovieCategoryGrid(scopedMovies, viewModel, onOpenMovie)
                        hasSources -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_provider))
                        else -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_add))
                    }
                    else -> if (titleFilter.isNotBlank() && filteredResume.isEmpty() &&
                        filteredRecommended.isEmpty() && filteredRecent.isEmpty() &&
                        filteredGenreRows.isEmpty()
                    ) {
                        EmptyVod("No matching movies", "No titles match this search in the current selection.")
                    } else LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (filteredResume.isNotEmpty()) item(key = "cw") {
                            ContinueWatchingRow(filteredResume, onResume, onOpenResumeDetails, viewModel)
                        }
                        if (filteredRecommended.isNotEmpty()) item(key = "rec") {
                            MoviePosterRow(stringResource(R.string.vod_recommended), filteredRecommended, onOpenMovie)
                        }
                        if (filteredRecent.isNotEmpty()) item(key = "recent") {
                            MoviePosterRow(stringResource(R.string.vod_recently_added), filteredRecent, onOpenMovie)
                        }
                        items(filteredGenreRows, key = { "g:${it.genre}" }) { group ->
                            MoviePosterRow(group.genre, group.items, onOpenMovie)
                        }
                    }
                }
                }
            }
        }
    }
}

/** Category sidebar on television, horizontally scrollable category chips on phones. */
@Composable
fun SeriesScreen(
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenResumeDetails: (VodViewModel.ResumeItem) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    isTelevision: Boolean = false,
    categoryJumpFocus: FocusRequester? = null,
    titleFilter: String = "",
    onTitleFilterChange: (String) -> Unit = {},
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.seriesCategories.collectAsState()
    val resume by viewModel.continueWatchingSeries.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSeries.collectAsState()
    val genreRows by viewModel.seriesGenreRows.collectAsState()
    val categorySeries by viewModel.series.collectAsState()
    val allSeries by viewModel.allSeries.collectAsState()
    val loadState by viewModel.seriesStatus.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedSource by viewModel.selectedVodSource.collectAsState()

    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }
    var browseCategory by remember { mutableStateOf<String?>(RECENTLY_ADDED_CATEGORY) }
    val matchTitle: (Series) -> Boolean = { series ->
        series.displayTitle.contains(titleFilter.trim(), ignoreCase = true) &&
            (selectedSource == null || series.sourceId == selectedSource)
    }
    val filteredResume = resume.filter { it.title.contains(titleFilter.trim(), ignoreCase = true) }
    val filteredRecent = recentlyAdded.filter(matchTitle)
    val filteredGenreRows = genreRows.mapNotNull { row ->
        val items = row.items.filter(matchTitle)
        if (items.isEmpty()) null else row.copy(items = items)
    }
    val hasContent = resume.isNotEmpty() || recentlyAdded.isNotEmpty() || genreRows.isNotEmpty()
    val onCategory: (String) -> Unit = { id ->
        // Keep the active query while switching categories: re-filter in place.
        browseCategory = id
        viewModel.selectSeriesCategory(if (isSpecialVodCategory(id)) null else id)
    }
    val onAll: () -> Unit = {
        browseCategory = null
        viewModel.selectSeriesCategory(null)
    }

    val scopedSeries = remember(allSeries, selectedSource) {
        if (selectedSource == null) allSeries else allSeries.filter { it.sourceId == selectedSource }
    }
    val counts = remember(scopedSeries, resume, recentlyAdded) {
        scopedSeries.mapNotNull { it.categoryId }.groupingBy { it }.eachCount() +
            mapOf(
                ALL_CATEGORY to scopedSeries.size,
                FAVOURITES_CATEGORY to scopedSeries.count { it.favourite },
                CONTINUE_WATCHING_CATEGORY to resume.size,
                RECENTLY_ADDED_CATEGORY to recentlyAdded.size,
            )
    }
    val entries = (vodSections() + categories.map { it.id to it.name }).distinctBy { it.first }
    Column(Modifier.fillMaxSize().background(Color(0xFF0C1526))) {
        if (sources.size > 1) ProviderChips(
            sources = sources,
            selected = selectedSource,
            onSelectAll = { browseCategory = RECENTLY_ADDED_CATEGORY; viewModel.selectVodSource(null) },
            onSelectSource = { id -> browseCategory = RECENTLY_ADDED_CATEGORY; viewModel.selectVodSource(id) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VodBrowserFrame(
                entries = entries,
                counts = counts,
                selected = browseCategory,
                isTelevision = isTelevision,
                onSelectHome = onAll,
                onSelectCategory = onCategory,
                categoryJumpFocus = categoryJumpFocus,
            ) {
                Box(Modifier.fillMaxSize()) {
                when {
                    (loadState.phase == VodLoadPhase.PENDING || loadState.phase == VodLoadPhase.LOADING) &&
                        allSeries.isEmpty() ->
                        LoadingVod(loadState.message.ifBlank { "Loading series…" })
                    loadState.phase == VodLoadPhase.FAILED && allSeries.isEmpty() ->
                        VodLoadFailure("Series are unavailable", loadState.message, viewModel::refreshVod)
                    loadState.phase == VodLoadPhase.READY && allSeries.isEmpty() && browseCategory == null ->
                        LoadingVod("Preparing series catalogue…")
                    browseCategory == CONTINUE_WATCHING_CATEGORY -> ContinueWatchingGrid(resume.filter {
                        it.title.contains(titleFilter.trim(), ignoreCase = true) }, onResume, onOpenResumeDetails, isTelevision, viewModel)
                    browseCategory == FAVOURITES_CATEGORY -> SeriesCategoryGrid(scopedSeries.filter {
                        it.favourite && it.displayTitle.contains(titleFilter.trim(), ignoreCase = true) }, onOpenSeries)
                    titleFilter.isNotBlank() && browseCategory == RECENTLY_ADDED_CATEGORY ->
                        PagedSeriesCategoryGrid(null, selectedSource, titleFilter, onOpenSeries, viewModel)
                    browseCategory == RECENTLY_ADDED_CATEGORY -> SeriesCategoryGrid(recentlyAdded.filter {
                        (selectedSource == null || it.sourceId == selectedSource) &&
                            it.displayTitle.contains(titleFilter.trim(), ignoreCase = true)
                    }, onOpenSeries)
                    browseCategory != null -> PagedSeriesCategoryGrid(
                        browseCategory, selectedSource, titleFilter, onOpenSeries, viewModel)
                    titleFilter.isNotBlank() && browseCategory == null ->
                        PagedSeriesCategoryGrid(null, selectedSource, titleFilter, onOpenSeries, viewModel)
                    !hasContent -> when {
                        loadState.phase == VodLoadPhase.LOADING || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_shows))
                        scopedSeries.isNotEmpty() -> SeriesCategoryGrid(scopedSeries, onOpenSeries)
                        hasSources -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_provider))
                        else -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_add))
                    }
                    else -> if (titleFilter.isNotBlank() && filteredResume.isEmpty() &&
                        filteredRecent.isEmpty() && filteredGenreRows.isEmpty()
                    ) {
                        EmptyVod("No matching series", "No titles match this search in the current selection.")
                    } else LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (filteredResume.isNotEmpty()) item(key = "cw") {
                            ContinueWatchingRow(filteredResume, onResume, onOpenResumeDetails, viewModel)
                        }
                        if (filteredRecent.isNotEmpty()) item(key = "recent") {
                            SeriesPosterRow(stringResource(R.string.vod_recently_added), filteredRecent, onOpenSeries)
                        }
                        items(filteredGenreRows, key = { "g:${it.genre}" }) { group ->
                            SeriesPosterRow(group.genre, group.items, onOpenSeries)
                        }
                    }
                }
                }
            }
        }
    }
}

/** TV-style category column inspired by established IPTV browse patterns. */
private const val CONTINUE_WATCHING_CATEGORY = "__myiptv_continue_watching__"
private const val FAVOURITES_CATEGORY = "__myiptv_favourites__"
private const val RECENTLY_ADDED_CATEGORY = "__myiptv_recently_added__"
private const val ALL_CATEGORY = "__myiptv_all__"

private fun isSpecialVodCategory(id: String): Boolean = id == CONTINUE_WATCHING_CATEGORY ||
    id == FAVOURITES_CATEGORY || id == RECENTLY_ADDED_CATEGORY

private fun vodSections() = listOf(
    FAVOURITES_CATEGORY to "Favourites",
    CONTINUE_WATCHING_CATEGORY to "Continue Watching",
    RECENTLY_ADDED_CATEGORY to "Recently Added",
)

@Composable
private fun ContinueWatchingGrid(
    items: List<VodViewModel.ResumeItem>,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenResumeDetails: (VodViewModel.ResumeItem) -> Unit,
    isTelevision: Boolean,
    viewModel: VodViewModel,
) {
    val scope = rememberCoroutineScope()
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing in Continue Watching yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF1D2C43), Color(0xFF070D18)),
    ))) {
        Text("CONTINUE WATCHING", color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (isTelevision) 160.dp else 130.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(items, key = { it.historyGroupKey }) { item ->
                ResumeCard(
                    item = item,
                    onClick = { onResume(item.mediaKey, item.streamUrl, item.title) },
                    onOpenDetails = { onOpenResumeDetails(item) },
                    onResetProgress = { scope.launch { viewModel.resetContinueWatching(item) } },
                )
            }
        }
    }
}

@Composable
private fun VodBrowserFrame(
    entries: List<Pair<String, String>>,
    counts: Map<String, Int>,
    selected: String?,
    isTelevision: Boolean,
    onSelectHome: () -> Unit,
    onSelectCategory: (String) -> Unit,
    categoryJumpFocus: FocusRequester?,
    content: @Composable () -> Unit,
) {
    val page = @Composable {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0xFF1B2B43), Color(0xFF07101B)),
        ))) { content() }
    }
    if (isTelevision) {
        Row(Modifier.fillMaxSize()) {
            VodCategorySidebar(entries, counts, selected, true, onSelectHome, onSelectCategory,
                focusJump = categoryJumpFocus)
            Box(Modifier.weight(1f).fillMaxHeight()) { page() }
        }
    } else {
        var drawerOpen by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
            page()
            if (drawerOpen) {
                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable { drawerOpen = false })
                VodCategorySidebar(
                    entries, counts, selected, true,
                    onSelectHome = { onSelectHome(); drawerOpen = false },
                    onSelectCategory = { onSelectCategory(it); drawerOpen = false },
                    modifier = Modifier.align(Alignment.CenterStart).zIndex(2f),
                    onDismiss = { drawerOpen = false },
                )
            } else {
                IconButton(
                    onClick = { drawerOpen = true },
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xFF087688), RoundedCornerShape(8.dp))
                        .zIndex(2f),
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Browse categories",
                        tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VodCategorySidebar(
    entries: List<Pair<String, String>>,
    counts: Map<String, Int>,
    selected: String?,
    isTelevision: Boolean,
    onSelectHome: () -> Unit,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    focusJump: FocusRequester? = null,
) {
    var expanded by remember(isTelevision) { mutableStateOf(isTelevision) }
    var categorySearch by remember { mutableStateOf("") }
    val matching = remember(entries, categorySearch) {
        if (categorySearch.isBlank()) entries
        else entries.filter { it.second.contains(categorySearch.trim(), ignoreCase = true) }
    }
    val chooseHome: () -> Unit = {
        onSelectHome()
        if (onDismiss != null) onDismiss() else if (!isTelevision) expanded = false
    }
    val chooseCategory: (String) -> Unit = { id ->
        onSelectCategory(id)
        if (onDismiss != null) onDismiss() else if (!isTelevision) expanded = false
    }
    Column(
        modifier = modifier
            .width(if (expanded) (if (isTelevision) 286.dp else 236.dp) else 62.dp)
            .fillMaxHeight()
            .background(Color(0xFF080A0D)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (onDismiss != null) onDismiss() else expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.Menu else Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Collapse categories" else "Expand categories",
                    tint = Color.White,
                )
            }
            if (expanded) Text("CATEGORIES", color = Color.White,
                style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        if (expanded) {
            OutlinedTextField(
                value = categorySearch,
                onValueChange = { categorySearch = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search categories", maxLines = 1) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                    .explicitImeActivation(),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (categorySearch.isBlank() || "all".contains(categorySearch.trim(), true)) {
                item(key = "all") {
                    VodCategoryRow("ALL", selected == null, expanded, counts[ALL_CATEGORY], chooseHome,
                        if (selected == null) focusJump else null)
                }
            }
            items(matching, key = { it.first }) { (id, label) ->
                VodCategoryRow(label, selected == id, expanded, counts[id],
                    onClick = { chooseCategory(id) },
                    focusJump = if (selected == id) focusJump else null)
            }
        }
    }
}

@Composable
private fun VodCategoryRow(
    label: String,
    selected: Boolean,
    expanded: Boolean,
    count: Int?,
    onClick: () -> Unit,
    focusJump: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .then(if (focusJump != null) Modifier.focusRequester(focusJump) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(when {
                focused -> Color(0xFF078499)
                selected -> Color(0xFF086A7C)
                else -> Color.Transparent
            })
            .clickable(onClick = onClick)
            .padding(horizontal = if (expanded) 12.dp else 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Text(
            if (expanded) label else label.take(2).uppercase(),
            modifier = if (expanded) Modifier.weight(1f) else Modifier,
            maxLines = if (expanded) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
        )
        if (expanded && count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), color = Color.White.copy(alpha = .88f),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- Whole-category browse grids ---------------------------------------------------------------

/**
 * Only materialise a bounded DB page. The user explicitly asks for another page rather than
 * a TV box allocating thousands of decoded posters or one enormous SQLite cursor.
 * Each category/filter/provider change resets its pagination state safely.
 */
@Composable
private fun PagedMovieCategoryGrid(
    categoryId: String?,
    sourceId: Long?,
    filter: String,
    viewModel: VodViewModel,
    onOpenMovie: (Movie) -> Unit,
) {
    // Fetch 160 rows per query. The former growing LIMIT silently stopped at 2,000.
    val pageSize = 160
    var pageIndex by remember(categoryId, sourceId, filter) {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    var loaded by remember(categoryId, sourceId, filter) { mutableStateOf(emptyList<Movie>()) }
    var hasMore by remember(categoryId, sourceId, filter) { mutableStateOf(false) }
    var loading by remember(categoryId, sourceId, filter) { mutableStateOf(true) }
    val offset = pageIndex * pageSize
    val pageFlow = remember(categoryId, sourceId, filter, offset) {
        viewModel.movieCategoryPage(categoryId, sourceId, filter.trim(), pageSize + 1, offset)
    }
    LaunchedEffect(pageFlow) {
        loading = true
        pageFlow.collect { slice ->
            // Retain previous cards and remove duplicates if catalogue updates move page boundaries.
            loaded = ((if (offset == 0) emptyList() else loaded.take(offset)) +
                slice.take(pageSize)).distinctBy { it.id }
            hasMore = slice.size > pageSize
            loading = false
        }
    }
    MovieCategoryGrid(
        movies = loaded, viewModel = viewModel, onOpenMovie = onOpenMovie,
        hasMore = hasMore, loadingMore = loading,
        onLoadMore = {
            if (hasMore && !loading) {
                loading = true
                pageIndex++
            }
        },
    )
}

/** One category's films as a poster grid. Quality variants collapse to one card, badged. */
@Composable
private fun MovieCategoryGrid(
    movies: List<Movie>,
    viewModel: VodViewModel,
    onOpenMovie: (Movie) -> Unit,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    if (movies.isEmpty() && loadingMore) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No matching movies in this category", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val posterSize by AppSettings.get(LocalContext.current).posterSize.collectAsState()
    // Provider categorisation/quality grouping may scan tens of thousands of titles.
    // Run it off the Compose/UI thread so category changes cannot freeze remote input.
    val groups by produceState(
        initialValue = emptyList<app.opentv.data.repo.MovieVariantGroup>(),
        key1 = movies,
    ) {
        value = emptyList()
        value = withContext(Dispatchers.Default) { viewModel.collapseVariants(movies) }
    }
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = posterSize.gridMinDp.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(groups, key = { it.primary.id }) { group ->
            val quality = group.variants.firstOrNull()?.qualityLabel?.takeIf { it.isNotBlank() }
            val badge = quality
                ?: if (group.hasMultipleQualities) {
                    stringResource(R.string.guide_qualities_count, group.variants.size)
                } else {
                    null
                }
            PosterCard(
                title = group.primary.displayTitle,
                posterUrl = group.primary.posterUrl,
                subtitle = group.primary.year?.toString(),
                rating = group.primary.rating,
                qualityBadge = badge,
                onClick = { onOpenMovie(group.primary) },
            )
        }
        if (hasMore) {
            item(key = "load-more") {
                androidx.compose.material3.OutlinedButton(
                    onClick = onLoadMore, enabled = !loadingMore,
                ) {
                    Text(if (loadingMore) "Loading…" else "Load 160 more")
                }
            }
        }
    }
}

/** Search and browse the complete cached series category, not just recent titles. */
@Composable
private fun PagedSeriesCategoryGrid(
    categoryId: String?,
    sourceId: Long?,
    filter: String,
    onOpenSeries: (Series) -> Unit,
    viewModel: VodViewModel,
) {
    val pageSize = 160
    var pageIndex by remember(categoryId, sourceId, filter) {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    var loaded by remember(categoryId, sourceId, filter) { mutableStateOf(emptyList<Series>()) }
    var hasMore by remember(categoryId, sourceId, filter) { mutableStateOf(false) }
    var loading by remember(categoryId, sourceId, filter) { mutableStateOf(true) }
    val offset = pageIndex * pageSize
    val pageFlow = remember(categoryId, sourceId, filter, offset) {
        viewModel.seriesCategoryPage(categoryId, sourceId, filter.trim(), pageSize + 1, offset)
    }
    LaunchedEffect(pageFlow) {
        loading = true
        pageFlow.collect { slice ->
            loaded = ((if (offset == 0) emptyList() else loaded.take(offset)) +
                slice.take(pageSize)).distinctBy { it.id }
            hasMore = slice.size > pageSize
            loading = false
        }
    }
    SeriesCategoryGrid(loaded, onOpenSeries, hasMore, loading) {
        if (hasMore && !loading) {
            loading = true
            pageIndex++
        }
    }
}

/** One category's shows as a poster grid. */
@Composable
private fun SeriesCategoryGrid(
    series: List<Series>, onOpenSeries: (Series) -> Unit,
    hasMore: Boolean = false, loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val posterSize by AppSettings.get(LocalContext.current).posterSize.collectAsState()
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadingMore) CircularProgressIndicator()
            else Text("No matching series in this category", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = posterSize.gridMinDp.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(series, key = { it.id }) { item ->
            PosterCard(
                title = item.displayTitle,
                posterUrl = item.posterUrl,
                subtitle = item.year?.toString(),
                rating = item.rating,
                onClick = { onOpenSeries(item) },
            )
        }
        if (hasMore) {
            item(key = "load-more-series") {
                androidx.compose.material3.OutlinedButton(
                    onClick = onLoadMore, enabled = !loadingMore,
                ) {
                    Text(if (loadingMore) "Loading…" else "Load 160 more")
                }
            }
        }
    }
}

// ---- Shared shelves ----------------------------------------------------------------------------

/** A titled horizontal shelf of movie poster cards. Shared by the home and the detail's "more like this". */
@Composable
internal fun MoviePosterRow(title: String, movies: List<Movie>, onOpenMovie: (Movie) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(movies, key = { it.id }) { movie ->
                PosterCard(
                    title = movie.displayTitle,
                    posterUrl = movie.posterUrl,
                    subtitle = movie.year?.toString(),
                    rating = movie.rating,
                    onClick = { onOpenMovie(movie) },
                )
            }
        }
    }
}

/** A titled horizontal shelf of series poster cards. */
@Composable
internal fun SeriesPosterRow(title: String, series: List<Series>, onOpenSeries: (Series) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(series, key = { it.id }) { item ->
                PosterCard(
                    title = item.displayTitle,
                    posterUrl = item.posterUrl,
                    subtitle = item.year?.toString(),
                    rating = item.rating,
                    onClick = { onOpenSeries(item) },
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

/**
 * The reusable poster card: art, title and an optional year, with a rating chip, a quality badge and
 * a resume progress bar drawn over the art where the data is there. The focused card scales up and
 * gains a primary border — the app's established focus cue — and, being focusable, the lazy row
 * brings it into view on its own.
 */
@Composable
internal fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: Double? = null,
    qualityBadge: String? = null,
    progress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val posterSize by AppSettings.get(LocalContext.current).posterSize.collectAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "posterScale")
    Column(
        modifier
            .width(posterSize.cardDp.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    else Modifier,
                ),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            rating?.takeIf { it > 0.0 }?.let {
                Badge(
                    text = "★ ${formatRating(it)}",
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
            qualityBadge?.let {
                Badge(
                    text = it,
                    highlight = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A small rounded chip drawn over poster art — a rating or a quality label. */
@Composable
private fun Badge(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary
    else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.66f)
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color.White
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---- Continue watching -------------------------------------------------------------------------

@Composable
internal fun ContinueWatchingRow(
    items: List<VodViewModel.ResumeItem>,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenResumeDetails: (VodViewModel.ResumeItem) -> Unit,
    viewModel: VodViewModel,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vod_continue_watching))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.mediaKey }) { item ->
                ResumeCard(item,
                    onOpenDetails = { onOpenResumeDetails(item) },
                    onResetProgress = { scope.launch { viewModel.resetContinueWatching(item) } },
                    onClick = { onResume(item.mediaKey, item.streamUrl, item.title) })
            }
        }
    }
}

/** Portrait series/movie art, with a clear episode identifier and persisted progress. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResumeCard(
    item: VodViewModel.ResumeItem,
    onFocus: () -> Unit = {},
    onOpenDetails: () -> Unit,
    onResetProgress: () -> Unit,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var okPressedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var longClickFired by remember { mutableStateOf(false) }
    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text(item.title) },
            text = { Text("Continue Watching options") },
            confirmButton = {
                Column {
                    TextButton(
                        enabled = item.movieDetailId != null || item.seriesDetailId != null,
                        onClick = { menuOpen = false; onOpenDetails() },
                    ) { Text("Open title page") }
                    TextButton(onClick = {
                        menuOpen = false
                        confirmReset = true
                    }) { Text("Remove and reset progress") }
                }
            },
            dismissButton = {
                TextButton(onClick = { menuOpen = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset watching progress?") },
            text = {
                Text(if (item.mediaKey.startsWith("ep:"))
                    "Remove the series from Continue Watching and reset all its episodes for this profile?"
                    else "Remove this movie from Continue Watching and reset its progress for this profile?")
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetProgress() }) {
                    Text("Reset progress")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "resumeScale")
    Column(
        Modifier
            .width(154.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(RoundedCornerShape(7.dp))
            // Handle physical remote OK/Enter on key-down/repeats/up directly.
            // Android TV remotes often do not generate Compose's touch long-click.
            .onPreviewKeyEvent { e ->
                val native = e.nativeKeyEvent
                when {
                    native.keyCode == android.view.KeyEvent.KEYCODE_MENU &&
                        e.type == KeyEventType.KeyDown -> {
                        menuOpen = true
                        true
                    }
                    native.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER &&
                        native.keyCode != android.view.KeyEvent.KEYCODE_ENTER &&
                        native.keyCode != android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> false
                    e.type == KeyEventType.KeyDown -> {
                        if (okPressedAt == 0L) {
                            okPressedAt = native.downTime
                            longClickFired = false
                        }
                        if (!longClickFired &&
                            native.eventTime - okPressedAt >= 500L) {
                            longClickFired = true
                            menuOpen = true
                        }
                        true
                    }
                    e.type == KeyEventType.KeyUp -> {
                        if (!longClickFired) {
                            if (native.eventTime - okPressedAt >= 500L) menuOpen = true
                            else onClick()
                        }
                        okPressedAt = 0L
                        longClickFired = false
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
                onLongClickLabel = "Continue Watching options",
            )
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1C2A3E))
                .then(if (focused) Modifier.border(3.dp, Color(0xFF15B9D0), RoundedCornerShape(6.dp)) else Modifier),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.episodeLabel != null) {
                Text(
                    item.episodeLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.63f))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                )
            }
            Box(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume playback",
                    tint = Color.White)
            }
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(item.title, color = Color.White, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.durationMillis > 0L) {
            val watchedMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(item.positionMillis)
            val durationMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(item.durationMillis)
            Text("${watchedMinutes}m / ${durationMinutes}m · ${(item.progress * 100).toInt()}% watched",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64DBE9), maxLines = 1)
        }
    }
}

// ---- Category chips ----------------------------------------------------------------------------

/**
 * A horizontal chip strip along the top of the home: an "All" chip returns to the curated rows, and
 * each following chip opens that category's full grid. Keeps whole-category browsing reachable on a
 * d-pad without a permanent side rail taking the width.
 */
@Composable
private fun CategoryChips(
    entries: List<Pair<String, String>>,
    selected: String?,
    onSelectHome: () -> Unit,
    onSelectCategory: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "label") {
            Text(
                stringResource(R.string.vod_categories),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        item(key = "all") { Chip(stringResource(R.string.vod_all), selected == null, onSelectHome) }
        items(entries, key = { it.first }) { (id, name) ->
            Chip(name, selected == id) { onSelectCategory(id) }
        }
    }
}

/**
 * A provider filter above the category chips, shown only when more than one source is configured —
 * pick a provider to browse just its Movies/Shows categories, or "All sources" to fold them.
 */
@Composable
private fun ProviderChips(
    sources: List<Source>,
    selected: Long?,
    onSelectAll: () -> Unit,
    onSelectSource: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "plabel") {
            Text(
                stringResource(R.string.channels_manager_source_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        item(key = "pall") {
            Chip(stringResource(R.string.channels_manager_all_sources), selected == null, onSelectAll)
        }
        items(sources, key = { it.id }) { source ->
            Chip(source.name, selected == source.id) { onSelectSource(source.id) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ---- Search / loading / empty ------------------------------------------------------------------

/**
 * The search entry at the top of the Movies and Shows home. The rail's global search already covers
 * movies and series; this makes it reachable without leaving the tab. Focusable for d-pad on TV and
 * tappable on touch, it just opens the existing search screen.
 */
@Composable
private fun SearchAffordance(onOpenSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else Color(0xFF1C2A43),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onOpenSearch)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (focused) MaterialTheme.colorScheme.onPrimary
        else Color.White
        Icon(Icons.Filled.Search, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.vod_search_hint),
            style = MaterialTheme.typography.titleMedium,
            color = tint,
        )
    }
}

@Composable
private fun EmptyVod(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Color(0xFFBDCBDF), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun LoadingVod(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.vod_large_provider_note),
                color = Color(0xFFB4C3D6),
            )
        }
    }
}

@Composable
private fun VodLoadFailure(title: String, message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(message, color = Color(0xFFB4C3D6),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            androidx.compose.material3.Button(onClick = onRetry) { Text("Retry loading") }
        }
    }
}

/** Poster shelf card width; the grid uses an adaptive min size close to this. */
// Poster width is determined by the saved poster-size setting.

/** Rating to one decimal place, locale-independent (the "★" is drawn beside it). */
internal fun formatRating(rating: Double): String = String.format(java.util.Locale.US, "%.1f", rating)
