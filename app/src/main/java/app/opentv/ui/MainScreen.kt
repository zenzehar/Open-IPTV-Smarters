/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 *
 * Independent Smarters-inspired, responsive TV and mobile navigation.
 */
package app.opentv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.StatusBus
import app.opentv.core.findActivity
import app.opentv.data.model.Channel
import app.opentv.data.model.Movie
import app.opentv.data.model.Recording
import app.opentv.data.model.Series
import app.opentv.ui.channels.HomeScreen
import app.opentv.ui.channels.MobileLiveScreen
import app.opentv.ui.recordings.RecordingsScreen
import app.opentv.ui.vod.MoviesScreen
import app.opentv.ui.vod.SeriesScreen

/**
 * TV: a full dashboard with large, remote-friendly feature tiles.
 * Phone: stacked dashboard and a fixed, touch-friendly bottom navigation.
 *
 * Never reuse TV rail widths on phones. The window can be resized without forcing
 * the entire screen to lay out at a desktop width.
 */
private enum class DashboardTab(val text: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    LIVE("Live TV", Icons.Filled.LiveTv),
    MOVIES("Movies", Icons.Filled.Movie),
    SERIES("Series", Icons.Filled.Tv),
    RECORDINGS("Recordings", Icons.Filled.FiberManualRecord),
}

@Composable
fun MainScreen(
    isTelevision: Boolean,
    hasSources: Boolean,
    isSyncing: Boolean,
    refreshStage: String?,
    refreshedStages: Set<String>,
    failedRefreshStages: Set<String>,
    failedRefreshMessages: Map<String, String>,
    refreshMessage: String?,
    liveCatalogueTimestamp: Long,
    onPlayChannel: (Channel) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshStage: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onPlayRecording: (Recording) -> Unit,
    onPlayCatchup: (mediaKey: String, url: String, title: String, ua: String) -> Unit,
    activeProfileName: String,
    vodViewModel: VodViewModel,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val liveEnabled by settings.liveEnabled.collectAsState()
    val moviesEnabled by settings.moviesEnabled.collectAsState()
    val seriesEnabled by settings.seriesEnabled.collectAsState()
    val landscapeHome by settings.landscapeHome.collectAsState()
    val liveUpdated by settings.liveUpdated.collectAsState()
    val moviesUpdated by settings.moviesUpdated.collectAsState()
    val seriesUpdated by settings.seriesUpdated.collectAsState()
    val movieLoad by vodViewModel.movieStatus.collectAsState()
    val seriesLoad by vodViewModel.seriesStatus.collectAsState()
    // Do not launch simultaneous VOD downloads while manual refresh is running.
    LaunchedEffect(hasSources, moviesEnabled, seriesEnabled, isSyncing) {
        if (hasSources && !isSyncing && (moviesEnabled || seriesEnabled))
            vodViewModel.ensureVodLoaded()
    }
    LaunchedEffect(isSyncing, refreshedStages, failedRefreshStages) {
        if (!isSyncing) vodViewModel.reconcileManualRefresh(refreshedStages, failedRefreshStages)
    }
    val catalogueLoading = hasSources && (
        (moviesEnabled && movieLoad.phase in listOf(VodLoadPhase.PENDING, VodLoadPhase.LOADING)) ||
        (seriesEnabled && seriesLoad.phase in listOf(VodLoadPhase.PENDING, VodLoadPhase.LOADING))
    )
    val blockNavigation = catalogueLoading || isSyncing
    fun tileState(key: String): String = when {
        key in failedRefreshStages -> "FAILED"
        isSyncing && refreshStage == key -> "UPDATING"
        isSyncing && key in refreshedStages -> "UPDATED"
        isSyncing -> "WAITING"
        key == "movies" && movieLoad.phase == VodLoadPhase.LOADING -> "UPDATING"
        key == "movies" && movieLoad.phase == VodLoadPhase.PENDING -> "WAITING"
        key == "movies" && movieLoad.phase == VodLoadPhase.FAILED -> "FAILED"
        key == "series" && seriesLoad.phase == VodLoadPhase.LOADING -> "UPDATING"
        key == "series" && seriesLoad.phase == VodLoadPhase.PENDING -> "WAITING"
        key == "series" && seriesLoad.phase == VodLoadPhase.FAILED -> "FAILED"
        else -> ""
    }
    val choices = remember(liveEnabled, moviesEnabled, seriesEnabled) {
        buildList {
            add(DashboardTab.HOME)
            if (liveEnabled) add(DashboardTab.LIVE)
            if (moviesEnabled) add(DashboardTab.MOVIES)
            if (seriesEnabled) add(DashboardTab.SERIES)
            add(DashboardTab.RECORDINGS)
        }
    }
    var tab by remember { mutableStateOf(DashboardTab.HOME) }
    val activity = context.findActivity()
    DisposableEffect(activity, tab, isTelevision, landscapeHome) {
        val before = activity?.requestedOrientation
        if (activity != null && !isTelevision) {
            activity.requestedOrientation = if (tab == DashboardTab.HOME && landscapeHome)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        onDispose { if (before != null) activity?.requestedOrientation = before }
    }
    if (tab !in choices) tab = DashboardTab.HOME
    var showExit by remember { mutableStateOf(false) }

    BackHandler(enabled = tab != DashboardTab.HOME) { tab = DashboardTab.HOME }
    BackHandler(enabled = tab == DashboardTab.HOME) { showExit = true }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text(stringResource(R.string.exit_title)) },
            text = { Text(stringResource(R.string.exit_body)) },
            confirmButton = {
                TextButton(onClick = { showExit = false; context.findActivity()?.finish() }) {
                    Text(stringResource(R.string.exit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExit = false }) {
                    Text(stringResource(R.string.exit_cancel))
                }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF080D1C))) {
        // Home deliberately uses the widescreen Smarters-style dashboard on phones.
        // Other sections remain mobile-optimised even when the Home was landscape.
        val compact = if (tab == DashboardTab.HOME) maxWidth < 700.dp
            else !isTelevision || maxWidth < 700.dp
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            DashboardHeader(
                title = if (tab == DashboardTab.HOME) "MyIPTV" else tab.text,
                isHome = tab == DashboardTab.HOME,
                onHome = { tab = DashboardTab.HOME },
                onSearch = onOpenSearch,
                onSettings = onOpenSettings,
                onProfiles = onOpenProfiles,
                profile = activeProfileName,
                compact = compact,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DashboardTab.HOME -> DashboardHome(
                        compact = compact,
                        isTelevision = isTelevision,
                        liveState = tileState("live"),
                        liveFailure = failedRefreshMessages["live"],
                        movieFailure = failedRefreshMessages["movies"] ?: movieLoad.message.takeIf { movieLoad.phase == VodLoadPhase.FAILED },
                        seriesFailure = failedRefreshMessages["series"] ?: seriesLoad.message.takeIf { seriesLoad.phase == VodLoadPhase.FAILED },
                        liveLastUpdated = liveUpdated.takeIf { it > 0L } ?: liveCatalogueTimestamp,
                        moviesLastUpdated = moviesUpdated.takeIf { it > 0L } ?: settings.vodSyncedAtMillis,
                        seriesLastUpdated = seriesUpdated.takeIf { it > 0L } ?: settings.vodSyncedAtMillis,
                        onRefreshStage = onRefreshStage,
                        movieState = tileState("movies"),
                        seriesState = tileState("series"),
                        liveEnabled = liveEnabled,
                        moviesEnabled = moviesEnabled,
                        seriesEnabled = seriesEnabled,
                        profile = activeProfileName,
                        onLive = { if (!blockNavigation) tab = DashboardTab.LIVE },
                        onMovies = { if (!blockNavigation && movieLoad.phase in listOf(VodLoadPhase.READY, VodLoadPhase.EMPTY)) tab = DashboardTab.MOVIES },
                        onSeries = { if (!blockNavigation && seriesLoad.phase in listOf(VodLoadPhase.READY, VodLoadPhase.EMPTY)) tab = DashboardTab.SERIES },
                        onGuide = { if (!blockNavigation) tab = DashboardTab.LIVE },
                        onRecordings = { if (!blockNavigation) tab = DashboardTab.RECORDINGS },
                        onSearch = onOpenSearch,
                        onSettings = onOpenSettings,
                        onAddSource = onAddSource,
                        onRefresh = onRefresh,
                    )
                    DashboardTab.LIVE -> {
                        if (compact) MobileLiveScreen(
                            hasSources = hasSources,
                            isSyncing = isSyncing,
                            onPlayChannel = onPlayChannel,
                            onAddSource = onAddSource,
                            onRefresh = onRefresh,
                        ) else HomeScreen(
                            isTelevision = true,
                            hasSources = hasSources,
                            isSyncing = isSyncing,
                            onPlayChannel = onPlayChannel,
                            onAddSource = onAddSource,
                            onRefresh = onRefresh,
                            onPlayCatchup = onPlayCatchup,
                        )
                    }
                    DashboardTab.MOVIES -> MoviesScreen(
                        viewModel = vodViewModel,
                        isTelevision = !compact,
                        onOpenMovie = onOpenMovie,
                        onResume = onResume,
                        onOpenSearch = onOpenSearch,
                        hasSources = hasSources,
                        isSyncing = isSyncing,
                    )
                    DashboardTab.SERIES -> SeriesScreen(
                        viewModel = vodViewModel,
                        isTelevision = !compact,
                        onOpenSeries = onOpenSeries,
                        onResume = onResume,
                        onOpenSearch = onOpenSearch,
                        hasSources = hasSources,
                        isSyncing = isSyncing,
                    )
                    DashboardTab.RECORDINGS -> RecordingsScreen(onPlay = onPlayRecording)
                }
            }
            if (tab != DashboardTab.HOME) StatusBar()
            if (compact && tab != DashboardTab.HOME) {
                MobileBottomNav(
                    tabs = choices.filter { it != DashboardTab.RECORDINGS },
                    selected = tab,
                    onSelect = { chosen ->
                        if (!blockNavigation && when (chosen) {
                            DashboardTab.MOVIES -> movieLoad.phase in listOf(VodLoadPhase.READY, VodLoadPhase.EMPTY)
                            DashboardTab.SERIES -> seriesLoad.phase in listOf(VodLoadPhase.READY, VodLoadPhase.EMPTY)
                            else -> true
                        }) tab = chosen
                    },
                    onSettings = onOpenSettings,
                )
            } else if (tab != DashboardTab.HOME) {
                TvNavigation(
                    tabs = choices,
                    selected = tab,
                    onSelect = { chosen ->
                        if (!blockNavigation && when (chosen) {
                            DashboardTab.MOVIES -> movieLoad.phase in listOf(VodLoadPhase.READY, VodLoadPhase.EMPTY)
                            DashboardTab.SERIES -> seriesLoad.phase in listOf(VodLoadPhase.READY, VodLoadPhase.EMPTY)
                            else -> true
                        }) tab = chosen
                    },
                )
            }
        }
        // Dim the dashboard and intercept input until all enabled catalogue
        // sections have finished. A failed fetch shows Retry instead of a fake
        // empty library, and does not trap the user behind an endless spinner.
        if (blockNavigation && tab != DashboardTab.HOME) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        if (isSyncing) (refreshStage?.let {
                            "Updating ${it.replaceFirstChar { c -> c.uppercase() }}…"
                        } ?: "Refreshing catalogues…") else "Loading movies & series…",
                        color = Color.White, style = MaterialTheme.typography.titleLarge,
                    )
                    if (isSyncing && refreshMessage != null) Text(
                        refreshMessage, color = Color(0xFFD0D8E7),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!isSyncing) Text(
                        listOfNotNull(
                            if (moviesEnabled) movieLoad.message.takeIf { it.isNotEmpty() } else null,
                            if (seriesEnabled) seriesLoad.message.takeIf { it.isNotEmpty() } else null,
                        ).joinToString("  ·  "),
                        color = Color(0xFFD0D8E7),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    title: String,
    isHome: Boolean,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onProfiles: () -> Unit,
    profile: String,
    compact: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1022))
            .padding(horizontal = if (compact) 12.dp else 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
    ) {
        if (!isHome) {
            HeaderAction(Icons.Filled.ArrowBack, "Dashboard", onHome)
        } else {
            Icon(Icons.Filled.Tv, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
        }
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!compact) {
            Text(profile, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 108.dp))
        }
        HeaderAction(Icons.Filled.Search, "Search", onSearch)
        HeaderAction(Icons.Filled.Person, "Profiles", onProfiles)
        HeaderAction(Icons.Filled.Settings, "Settings", onSettings)
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else Color(0xFF172242))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White)
    }
}

@Composable
private fun DashboardHome(
    compact: Boolean,
    isTelevision: Boolean,
    liveState: String,
    liveFailure: String?,
    movieFailure: String?,
    seriesFailure: String?,
    liveLastUpdated: Long,
    moviesLastUpdated: Long,
    seriesLastUpdated: Long,
    onRefreshStage: (String) -> Unit,
    movieState: String,
    seriesState: String,
    liveEnabled: Boolean,
    moviesEnabled: Boolean,
    seriesEnabled: Boolean,
    profile: String,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onGuide: () -> Unit,
    onRecordings: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
) {
    val landscapePhone = !compact && !isTelevision
    val pad = if (compact || landscapePhone) 14.dp else 36.dp
    val liveHeight = if (landscapePhone) 244.dp else 310.dp
    val vodHeight = if (landscapePhone) 157.dp else 210.dp
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF0A1023)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = pad, vertical = if (compact) 18.dp else 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 26.dp),
    ) {
        if (compact) item(key = "welcome") {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (compact) "Welcome back" else "Your entertainment, your way",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (compact) profile else "Watch live TV, explore movies and catch up on your series.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9FAFCC),
                )
            }
        }
        if (compact) {
            if (liveEnabled) item(key = "live") {
                DashboardTile("LIVE TV", "Browse channels and guide", Icons.Filled.LiveTv,
                    listOf(Color(0xFF126EB7), Color(0xFF4935BC)), 148.dp, Modifier.fillMaxWidth(), onLive, liveState,
                    liveLastUpdated, { onRefreshStage("live") }, liveFailure)
            }
            if (moviesEnabled || seriesEnabled) item(key = "vod") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (moviesEnabled) DashboardTile("MOVIES", "On demand", Icons.Filled.Movie,
                        listOf(Color(0xFFF65457), Color(0xFFFF9729)), 155.dp, Modifier.weight(1f), onMovies, movieState,
                        moviesLastUpdated, { onRefreshStage("movies") }, movieFailure)
                    if (seriesEnabled) DashboardTile("SERIES", "Seasons & episodes", Icons.Filled.Tv,
                        listOf(Color(0xFF9631B8), Color(0xFF168DD8)), 155.dp, Modifier.weight(1f), onSeries, seriesState,
                        seriesLastUpdated, { onRefreshStage("series") }, seriesFailure)
                }
            }
            item(key = "quick-title") {
                Text("Quick access", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            item(key = "quick") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (liveEnabled) ShortcutTile("TV GUIDE", Icons.Filled.LiveTv, Modifier.weight(1f), onGuide)
                        ShortcutTile("RECORDINGS", Icons.Filled.FiberManualRecord, Modifier.weight(1f), onRecordings)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShortcutTile("SEARCH", Icons.Filled.Search, Modifier.weight(1f), onSearch)
                        ShortcutTile("SETTINGS", Icons.Filled.Settings, Modifier.weight(1f), onSettings)
                    }
                    ShortcutTile("UPDATE ALL CONTENT", Icons.Filled.Refresh, Modifier.fillMaxWidth(), onRefresh)
                }
            }
        } else {
            // On televisions the large Live tile anchors the left, with Movies/Series
            // above three quick actions on the right, matching familiar IPTV layouts.
            item(key = "tv-showcase") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (liveEnabled) DashboardTile(
                        "LIVE TV", "Live channels & guide", Icons.Filled.LiveTv,
                        listOf(Color(0xFF29CAA0), Color(0xFF355CDE)),
                        liveHeight, Modifier.weight(1.05f), onLive, liveState,
                        liveLastUpdated, { onRefreshStage("live") }, liveFailure,
                    )
                    Column(
                        modifier = Modifier.weight(1.95f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (moviesEnabled) DashboardTile(
                                "MOVIES", "Films on demand", Icons.Filled.Movie,
                                listOf(Color(0xFFEA365A), Color(0xFFF89A38)),
                                vodHeight, Modifier.weight(1f), onMovies, movieState,
                                moviesLastUpdated, { onRefreshStage("movies") }, movieFailure,
                            )
                            if (seriesEnabled) DashboardTile(
                                "SERIES", "Seasons & episodes", Icons.Filled.Tv,
                                listOf(Color(0xFF963AAE), Color(0xFF3995DB)),
                                vodHeight, Modifier.weight(1f), onSeries, seriesState,
                                seriesLastUpdated, { onRefreshStage("series") }, seriesFailure,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (liveEnabled) ShortcutTile(
                                "LIVE WITH EPG", Icons.Filled.LiveTv,
                                Modifier.weight(1f), onGuide,
                            )
                            ShortcutTile(
                                "RECORDINGS", Icons.Filled.FiberManualRecord,
                                Modifier.weight(1f), onRecordings,
                            )
                            ShortcutTile(
                                "UPDATE", Icons.Filled.Refresh,
                                Modifier.weight(1f), onRefresh,
                            )
                        }
                    }
                }
            }
        }
        item(key = "manage") {
            Text(
                "Manage playlists",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF93A6CA),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddSource)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/**
 * Home tile matching the reference hierarchy: artwork + title above a translucent
 * last-updated footer with its own refresh action. A stage's progress stays ON
 * its tile; other stages fade while they wait. It never obscures the whole page.
 */
@Composable
private fun DashboardTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    status: String = "",
    lastUpdated: Long = 0L,
    onRefresh: () -> Unit = {},
    failureReason: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var showFailureDetails by remember { mutableStateOf(false) }
    if (showFailureDetails) {
        AlertDialog(
            onDismissRequest = { showFailureDetails = false },
            title = { Text("$title update failed") },
            text = { Text(failureReason ?: "Could not reach the provider. Please retry.") },
            confirmButton = {
                TextButton(onClick = { showFailureDetails = false; onRefresh() }) {
                    Text("Retry")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFailureDetails = false }) { Text("Close") }
            },
        )
    }
    val compactTile = height < 185.dp
    val footerHeight = if (compactTile) 43.dp else 48.dp
    Column(
        modifier
            .height(height)
            .alpha(when (status) {
                "WAITING" -> 0.38f
                "FAILED" -> 0.58f
                else -> 1f
            })
            .clip(RoundedCornerShape(if (compactTile) 12.dp else 16.dp))
            .background(Brush.linearGradient(gradient)),
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(if (compactTile) 39.dp else 63.dp))
                Spacer(Modifier.height(if (compactTile) 4.dp else 9.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                    style = if (compactTile) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleLarge,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (focused) Text("SELECT", color = Color.White,
                    style = MaterialTheme.typography.labelSmall)
            }
            if (status == "UPDATING") {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .67f)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(if (compactTile) 22.dp else 39.dp),
                            strokeWidth = 3.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Updating", color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(footerHeight)
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(start = 10.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stamp = when (status) {
                "UPDATING" -> "Updating…"
                "WAITING" -> "Waiting…"
                "FAILED" -> "Failed · details"
                else -> if (lastUpdated > 0L) {
                    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
                        lastUpdated, System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                    if (compactTile) "Updated: $ago" else "Last updated: $ago"
                } else "Not updated yet"
            }
            Text(stamp, color = Color.White,
                style = if (compactTile) MaterialTheme.typography.labelSmall
                else MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).clickable(enabled = status == "FAILED") {
                    showFailureDetails = true
                }, maxLines = 1,
                softWrap = false, overflow = TextOverflow.Ellipsis)
            IconButton(
                onClick = onRefresh,
                enabled = status != "UPDATING" && status != "WAITING",
                modifier = Modifier
                    .size(if (compactTile) 36.dp else 39.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh $title",
                    tint = Color(0xFF141B2C), modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun ShortcutTile(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(66.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFF2CBB9A) else Color(0xFF238F89))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MobileBottomNav(
    tabs: List<DashboardTab>,
    selected: DashboardTab,
    onSelect: (DashboardTab) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF101A31)).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tab ->
            BottomNavItem(tab.text, tab.icon, selected == tab, Modifier.weight(1f)) { onSelect(tab) }
        }
        BottomNavItem("Settings", Icons.Filled.Settings, false, Modifier.weight(1f), onSettings)
    }
}

@Composable
private fun BottomNavItem(
    title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    Column(
        modifier.height(58.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = title,
            tint = if (selected) Color(0xFF64C7FF) else Color(0xFFA1AAC2),
            modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(title, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) Color.White else Color(0xFFA1AAC2))
    }
}

@Composable
private fun TvNavigation(
    tabs: List<DashboardTab>, selected: DashboardTab, onSelect: (DashboardTab) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF10182E)).padding(horizontal = 22.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tabs.forEach { t ->
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (focused) MaterialTheme.colorScheme.primary else if (selected == t)
                        Color(0xFF25436B) else Color.Transparent)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(t) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, null, modifier = Modifier.size(18.dp),
                    tint = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White)
                Spacer(Modifier.width(7.dp))
                Text(t.text, color = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White)
            }
        }
    }
}

@Composable
private fun StatusBar() {
    val message by StatusBus.message.collectAsState()
    val progress by StatusBus.progress.collectAsState()
    val status = message ?: return
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (progress == null) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Text("${(progress!! * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress!! },
                modifier = Modifier.fillMaxWidth().height(2.dp))
        }
    }
}
