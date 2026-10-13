/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 *
 * On-device fallback decoder for unusual IPTV containers.
 * LibVLC is distributed under LGPL-2.1-or-later (see VideoLAN).
 */
package app.opentv.ui.vod

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.opentv.core.ServiceLocator
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.atomic.AtomicBoolean
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Native VLC decoder is activated when Media3 reports unsupported parsing/container format.
 * Keeps the same mediaKey and profile-based resume history as the original player.
 * It does NOT silently send a stream URL or provider password to a third-party service.
 */
@Composable
fun VlcVodFallback(
    mediaKey: String,
    streamUrl: String,
    title: String,
    userAgent: String,
    onBack: () -> Unit,
    nextEpisode: Episode? = null,
    autoplayNext: Boolean = false,
    onAutoplayChange: (Boolean) -> Unit = {},
    onNextEpisode: () -> Unit = {},
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val profileId by graph.settings.activeProfileId.collectAsState()
    val scope = rememberCoroutineScope()
    var layout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var attachedLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var startedVideo by remember(mediaKey, streamUrl) { mutableStateOf(false) }
    var showStartupSpinner by remember(mediaKey, streamUrl) { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var movingSlider by remember { mutableStateOf(false) }
    val controlsTimeoutSeconds by graph.settings.controlsTimeoutSeconds.collectAsState()
    var controlsInteraction by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, controlsInteraction, playing, controlsTimeoutSeconds,
        movingSlider, errorText) {
        if (controlsVisible && playing && !movingSlider && errorText == null &&
            controlsTimeoutSeconds > 0) {
            delay(controlsTimeoutSeconds * 1_000L)
            controlsVisible = false
        }
    }
    var sliderValue by remember { mutableStateOf(0f) }
    var initialized by remember(mediaKey, streamUrl) { mutableStateOf(false) }
    var finished by remember(mediaKey) { mutableStateOf(false) }
    var nextRequested by remember(mediaKey) { mutableStateOf(false) }
    // The native listener is installed once; track latest settings and episode discovery
    // instead of capturing the initial null next episode or initial autoplay setting.
    val newestNext by rememberUpdatedState(nextEpisode)
    val newestAutoplay by rememberUpdatedState(autoplayNext)
    val newestNextAction by rememberUpdatedState(onNextEpisode)
    fun goNextOnce() {
        if (newestNext != null && !nextRequested) {
            nextRequested = true
            newestNextAction()
        }
    }

    // 750 ms reduces startup latency for VOD. Avoid setting a second contradictory cache
    // on the Media instance; unreliable provider networks may still buffer as needed.
    val libVlc = remember { LibVLC(context.applicationContext, arrayListOf("--network-caching=750", "--http-reconnect")) }
    val player = remember { MediaPlayer(libVlc) }
    val shuttingDown = remember { AtomicBoolean(false) }

    fun openExternal() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(streamUrl), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open in another player"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Install VLC or another compatible video player", Toast.LENGTH_LONG).show()
        }
    }

    suspend fun persistPosition() {
        if (finished) return // Never overwrite the completed episode with an end-of-stream zero.
        val pos = player.time.coerceAtLeast(0L)
        val duration = player.length.coerceAtLeast(0L)
        if (pos > 5_000L && duration > 0L) {
            graph.playbackPositions.upsert(PlaybackPosition(
                profileId = profileId,
                mediaKey = mediaKey,
                positionMillis = pos,
                durationMillis = duration,
                updatedAtMillis = System.currentTimeMillis(),
            ))
        }
    }

    BackHandler { onBack() }

    LaunchedEffect(mediaKey, streamUrl) {
        delay(700L)
        showStartupSpinner = true
    }

    LaunchedEffect(layout, streamUrl) {
        val surface = layout ?: return@LaunchedEffect
        // AndroidView can recreate its surface while the player is already attached.
        // Do not attach the same VLC MediaPlayer again: it produces the exact warning
        // "Can't set view when already attached" despite playback working underneath.
        if (initialized) {
            // The Compose AndroidView can be replaced on rotation or resize.
            // Keep playback alive, but rebind the native video output to the new view.
            if (attachedLayout !== surface) {
                try {
                    if (player.vlcVout.areViewsAttached()) player.detachViews()
                    player.attachViews(surface, null, true, false)
                    attachedLayout = surface
                    errorText = null
                } catch (e: Exception) {
                    errorText = "VLC video surface could not be restored: ${e.localizedMessage ?: "unknown error"}"
                }
            }
            return@LaunchedEffect
        }
        try {
            val resumeFrom = graph.playbackPositions.get(profileId, mediaKey)
                ?.takeIf { !it.isFinished }?.positionMillis ?: 0L
            if (player.vlcVout.areViewsAttached()) player.detachViews()
            player.attachViews(surface, null, true, false)
            attachedLayout = surface
            initialized = true
            errorText = null
            var resumeApplied = false
            player.setEventListener { event ->
                if (!shuttingDown.get()) scope.launch {
                    if (shuttingDown.get()) return@launch
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            buffering = false
                            playing = true
                            startedVideo = true
                            errorText = null
                            if (resumeFrom > 0L && !resumeApplied) {
                                // A pause/resume must not jump back to the original timestamp.
                                resumeApplied = true
                                runCatching { player.setTime(resumeFrom) }
                            }
                        }
                        MediaPlayer.Event.Paused -> playing = false
                        MediaPlayer.Event.Vout -> {
                            // A video output exists: never obscure the picture with "Opening".
                            startedVideo = true
                            buffering = false
                        }
                        MediaPlayer.Event.Buffering, MediaPlayer.Event.Opening -> {
                            // VLC can emit buffering updates long after video is visible.
                            if (!startedVideo) buffering = true
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            errorText = "VLC could not decode this stream. Check provider access or try an external player."
                            buffering = false
                        }
                        MediaPlayer.Event.EndReached -> {
                            playing = false
                            buffering = false
                            finished = true
                            val length = maxOf(player.length, durationMs).coerceAtLeast(0L)
                            if (length > 0L) {
                                graph.playbackPositions.upsert(PlaybackPosition(
                                    profileId, mediaKey, length, length, System.currentTimeMillis(),
                                ))
                            }
                            if (newestAutoplay && newestNext != null) goNextOnce()
                        }
                    }
                }
            }
            val media = Media(libVlc, Uri.parse(streamUrl))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":http-user-agent=${userAgent.replace("\n", "").replace("\r", "")}")
            player.media = media
            media.release()
            player.play()
        } catch (e: Exception) {
            // A valid stream can already be playing when a stale surface tries reattachment.
            // Don't cover the video with a false error in that case.
            if (player.vlcVout.areViewsAttached() && player.isPlaying) {
                initialized = true
                errorText = null
                buffering = false
            } else {
                initialized = false
                errorText = "Unable to start VLC video: ${e.localizedMessage ?: "unknown error"}"
                buffering = false
            }
        }
    }

    LaunchedEffect(mediaKey) {
        while (isActive) {
            delay(1_000L)
            val p = runCatching { player.time }.getOrDefault(0L)
            val d = runCatching { player.length }.getOrDefault(0L)
            durationMs = d.coerceAtLeast(0L)
            positionMs = if (durationMs > 0L) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
        }
    }
    LaunchedEffect(mediaKey) {
        while (isActive) {
            delay(10_000L)
            persistPosition()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            shuttingDown.set(true)
            runCatching { player.setEventListener(null) }
            // Use a separate IO scope: composition scope is cancelled on navigation.
            val p = runCatching { player.time }.getOrDefault(0L)
            val d = runCatching { player.length }.getOrDefault(0L)
            if (!finished && p > 5_000L && d > 0L) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    graph.playbackPositions.upsert(PlaybackPosition(
                        profileId, mediaKey, p, d, System.currentTimeMillis(),
                    ))
                }
            }
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            attachedLayout = null
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clickable {
                controlsVisible = !controlsVisible
                controlsInteraction++
            },
            factory = { ctx -> VLCVideoLayout(ctx).also { layout = it } },
        )
        // Show a small startup indicator only until the first playback/video-output event.
        // Never paint a large "Opening" label over video that is already rendering.
        if (buffering && !startedVideo && showStartupSpinner && errorText == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
        if (errorText != null && !playing) {
            Column(
                Modifier.align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(Color.Black.copy(alpha = 0.87f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Playback problem", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
                Text(errorText.orEmpty().take(250), color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 5)
                Button(onClick = ::openExternal) { Text("Open in another player", maxLines = 1) }
            }
        }
        if (controlsVisible) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(12.dp).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(10.dp)),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to library", tint = Color.White)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("VLC", color = Color(0xFF68D6CD))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayTime(positionMs), color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = if (movingSlider) sliderValue else positionMs.toFloat(),
                        onValueChange = { sliderValue = it; movingSlider = true },
                        onValueChangeFinished = {
                            runCatching { player.setTime(sliderValue.toLong()) }
                            movingSlider = false
                        },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(displayTime(durationMs), color = Color.White)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        runCatching { player.setTime((player.time - 10_000L).coerceAtLeast(0L)) }
                    }) {
                        Icon(Icons.Filled.FastRewind, contentDescription = "Back 10 seconds", tint = Color.White)
                    }
                    IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play", tint = Color.White)
                    }
                    IconButton(onClick = {
                        runCatching { player.setTime((player.time + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))) }
                    }) {
                        Icon(Icons.Filled.FastForward, contentDescription = "Forward 10 seconds", tint = Color.White)
                    }
                    if (nextEpisode != null) {
                        IconButton(onClick = { goNextOnce() }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next episode", tint = Color.White)
                        }
                    }
                    IconButton(onClick = ::openExternal) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "External player", tint = Color.White)
                    }
                }
                if (nextEpisode != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Auto-play next episode", color = Color.White,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            maxLines = 2)
                        Switch(checked = autoplayNext, onCheckedChange = onAutoplayChange)
                    }
                }
            }
        }
    }
}

private fun displayTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1_000L
    return if (seconds >= 3_600L)
        "%d:%02d:%02d".format(seconds / 3_600, (seconds / 60) % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
