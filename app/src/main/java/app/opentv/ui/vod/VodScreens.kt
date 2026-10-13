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
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    isTelevision: Boolean = false,
    categoryJumpFocus: FocusRequester? = null,
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.movieCategories.collectAsState()
    val resume by viewModel.continueWatchingMovies.collectAsState()
    val recommended by viewModel.recommendedMovies.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedMovies.collectAsState()
    val genreRows by viewModel.movieGenreRows.collectAsState()
    val categoryMovies by viewModel.movies.collectAsState()
    val allMovies by viewModel.allMovies.collectAsState()
    val loadState by viewModel.movieStatus.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedSource by viewModel.selectedVodSource.collectAsState()

    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }
    var browseCategory by remember { mutableStateOf<String?>(RECENTLY_ADDED_CATEGORY) }
    var titleFilter by remember { mutableStateOf("") }
    val hasContent = resume.isNotEmpty() || recommended.isNotEmpty() ||
        recentlyAdded.isNotEmpty() || genreRows.isNotEmpty()
    val onCategory: (String) -> Unit = { id ->
        titleFilter = ""
        browseCategory = id
        viewModel.selectMovieCategory(if (isSpecialVodCategory(id)) null else id)
    }
    val onAll: () -> Unit = {
        titleFilter = ""
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
        if (!isTelevision) SearchAffordance(onOpenSearch)
        if (sources.size > 1) ProviderChips(
            sources = sources,
            selected = selectedSource,
            onSelectAll = { browseCategory = RECENTLY_ADDED_CATEGORY; titleFilter = ""; viewModel.selectVodSource(null) },
            onSelectSource = { id -> browseCategory = RECENTLY_ADDED_CATEGORY; titleFilter = ""; viewModel.selectVodSource(id) },
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
                FilteredCategorySurface(titleFilter, { titleFilter = it }, isTelevision) {
                when {
                    (loadState.phase == VodLoadPhase.PENDING || loadState.phase == VodLoadPhase.LOADING) &&
                        allMovies.isEmpty() ->
                        LoadingVod(loadState.message.ifBlank { "Loading movies…" })
                    loadState.phase == VodLoadPhase.FAILED && allMovies.isEmpty() ->
                        VodLoadFailure("Movies are unavailable", loadState.message, viewModel::refreshVod)
                    loadState.phase == VodLoadPhase.READY && allMovies.isEmpty() && browseCategory == null ->
                        LoadingVod("Preparing movie catalogue…")
                    browseCategory == CONTINUE_WATCHING_CATEGORY -> ContinueWatchingGrid(resume.filter {
                        it.title.contains(titleFilter.trim(), ignoreCase = true) }, onResume, isTelevision)
                    browseCategory == FAVOURITES_CATEGORY -> MovieCategoryGrid(scopedMovies.filter {
                        it.favourite && it.displayTitle.contains(titleFilter.trim(), ignoreCase = true) },
                        viewModel, onOpenMovie)
                    browseCategory == RECENTLY_ADDED_CATEGORY -> MovieCategoryGrid(recentlyAdded.filter {
                        (selectedSource == null || it.sourceId == selectedSource) &&
                            it.displayTitle.contains(titleFilter.trim(), ignoreCase = true)
                    }, viewModel, onOpenMovie)
                    browseCategory != null -> MovieCategoryGrid(categoryMovies.filter {
                        (selectedSource == null || it.sourceId == selectedSource) &&
                            it.displayTitle.contains(titleFilter.trim(), ignoreCase = true)
                    }, viewModel, onOpenMovie)
                    !hasContent -> when {
                        loadState.phase == VodLoadPhase.LOADING || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_movies))
                        scopedMovies.isNotEmpty() -> MovieCategoryGrid(scopedMovies, viewModel, onOpenMovie)
                        hasSources -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_provider))
                        else -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_add))
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (resume.isNotEmpty()) item(key = "cw") { ContinueWatchingRow(resume, onResume) }
                        if (recommended.isNotEmpty()) item(key = "rec") {
                            MoviePosterRow(stringResource(R.string.vod_recommended), recommended, onOpenMovie)
                        }
                        if (recentlyAdded.isNotEmpty()) item(key = "recent") {
                            MoviePosterRow(stringResource(R.string.vod_recently_added), recentlyAdded, onOpenMovie)
                        }
                        items(genreRows, key = { "g:${it.genre}" }) { group ->
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
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    isTelevision: Boolean = false,
    categoryJumpFocus: FocusRequester? = null,
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
    var titleFilter by remember { mutableStateOf("") }
    val hasContent = resume.isNotEmpty() || recentlyAdded.isNotEmpty() || genreRows.isNotEmpty()
    val onCategory: (String) -> Unit = { id ->
        titleFilter = ""
        browseCategory = id
        viewModel.selectSeriesCategory(if (isSpecialVodCategory(id)) null else id)
    }
    val onAll: () -> Unit = {
        titleFilter = ""
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
        if (!isTelevision) SearchAffordance(onOpenSearch)
        if (sources.size > 1) ProviderChips(
            sources = sources,
            selected = selectedSource,
            onSelectAll = { browseCategory = RECENTLY_ADDED_CATEGORY; titleFilter = ""; viewModel.selectVodSource(null) },
            onSelectSource = { id -> browseCategory = RECENTLY_ADDED_CATEGORY; titleFilter = ""; viewModel.selectVodSource(id) },
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
                FilteredCategorySurface(titleFilter, { titleFilter = it }, isTelevision) {
                when {
                    (loadState.phase == VodLoadPhase.PENDING || loadState.phase == VodLoadPhase.LOADING) &&
                        allSeries.isEmpty() ->
                        LoadingVod(loadState.message.ifBlank { "Loading series…" })
                    loadState.phase == VodLoadPhase.FAILED && allSeries.isEmpty() ->
                        VodLoadFailure("Series are unavailable", loadState.message, viewModel::refreshVod)
                    loadState.phase == VodLoadPhase.READY && allSeries.isEmpty() && browseCategory == null ->
                        LoadingVod("Preparing series catalogue…")
                    browseCategory == CONTINUE_WATCHING_CATEGORY -> ContinueWatchingGrid(resume.filter {
                        it.title.contains(titleFilter.trim(), ignoreCase = true) }, onResume, isTelevision)
                    browseCategory == FAVOURITES_CATEGORY -> SeriesCategoryGrid(scopedSeries.filter {
                        it.favourite && it.displayTitle.contains(titleFilter.trim(), ignoreCase = true) }, onOpenSeries)
                    browseCategory == RECENTLY_ADDED_CATEGORY -> SeriesCategoryGrid(recentlyAdded.filter {
                        (selectedSource == null || it.sourceId == selectedSource) &&
                            it.displayTitle.contains(titleFilter.trim(), ignoreCase = true)
                    }, onOpenSeries)
                    browseCategory != null -> SeriesCategoryGrid(categorySeries.filter {
                        (selectedSource == null || it.sourceId == selectedSource) &&
                            it.displayTitle.contains(titleFilter.trim(), ignoreCase = true)
                    }, onOpenSeries)
                    !hasContent -> when {
                        loadState.phase == VodLoadPhase.LOADING || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_shows))
                        scopedSeries.isNotEmpty() -> SeriesCategoryGrid(scopedSeries, onOpenSeries)
                        hasSources -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_provider))
                        else -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_add))
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (resume.isNotEmpty()) item(key = "cw") { ContinueWatchingRow(resume, onResume) }
                        if (recentlyAdded.isNotEmpty()) item(key = "recent") {
                            SeriesPosterRow(stringResource(R.string.vod_recently_added), recentlyAdded, onOpenSeries)
                        }
                        items(genreRows, key = { "g:${it.genre}" }) { group ->
                            SeriesPosterRow(group.genre, group.items, onOpenSeries)
                        }
                    }
                }
                }
            }
        }
    }
}

/** Local title search preserves the active category and never starts a provider/API query. */
@Composable
private fun FilteredCategorySurface(
    filter: String,
    onChange: (String) -> Unit,
    isTelevision: Boolean,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = onChange,
            label = { Text("Filter titles in this category") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .padding(start = if (isTelevision) 12.dp else 54.dp,
                    end = 12.dp, top = 4.dp, bottom = 4.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
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
    isTelevision: Boolean,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing in Continue Watching yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var focusedResume by remember(items) { mutableStateOf(items.first().mediaKey) }
    val highlighted = items.firstOrNull { it.mediaKey == focusedResume } ?: items.first()
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF1D2C43), Color(0xFF070D18)),
    ))) {
        Text("CONTINUE WATCHING", color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
        Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (isTelevision) 160.dp else 130.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = if (isTelevision) Modifier.weight(1.5f).fillMaxHeight() else Modifier.fillMaxSize(),
            ) {
                gridItems(items, key = { it.historyGroupKey }) { item ->
                    ResumeCard(item, onClick = {
                        onResume(item.mediaKey, item.streamUrl, item.title)
                    }, onFocus = { focusedResume = item.mediaKey })
                }
            }
            if (isTelevision) {
                // A lightweight title preview (not a second active IPTV stream).
                Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp),
                    verticalArrangement = Arrangement.Center) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101A2B))) {
                        AsyncImage(model = highlighted.posterUrl,
                            contentDescription = highlighted.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(highlighted.title, color = Color.White,
                        style = MaterialTheme.typography.titleLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    highlighted.episodeLabel?.let {
                        Text(it, color = Color(0xFF62D8DF),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Select the poster to resume playback",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall)
                }
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
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

/** One category's films as a poster grid. Quality variants collapse to one card, badged. */
@Composable
private fun MovieCategoryGrid(movies: List<Movie>, viewModel: VodViewModel, onOpenMovie: (Movie) -> Unit) {
    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No matching movies in this category", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val posterSize by AppSettings.get(LocalContext.current).posterSize.collectAsState()
    val groups = remember(movies) { viewModel.collapseVariants(movies) }
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
    }
}

/** One category's shows as a poster grid. */
@Composable
private fun SeriesCategoryGrid(series: List<Series>, onOpenSeries: (Series) -> Unit) {
    val posterSize by AppSettings.get(LocalContext.current).posterSize.collectAsState()
    if (series.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No matching series in this category", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vod_continue_watching))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.mediaKey }) { item ->
                ResumeCard(item) { onResume(item.mediaKey, item.streamUrl, item.title) }
            }
        }
    }
}

/** Portrait series/movie art, with a clear episode identifier and persisted progress. */
@Composable
private fun ResumeCard(
    item: VodViewModel.ResumeItem,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
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
            .clickable(onClick = onClick)
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
