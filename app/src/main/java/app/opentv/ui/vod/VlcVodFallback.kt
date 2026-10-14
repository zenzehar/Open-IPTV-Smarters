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
import android.os.SystemClock
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
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.opentv.core.ServiceLocator
import app.opentv.player.VodSeekPolicy
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
    onEpisodes: () -> Unit = {},
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val profileId by graph.settings.activeProfileId.collectAsState()
    val controlsTimeout by graph.settings.playerControlsTimeoutSeconds.collectAsState()
    val seekFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var layout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var attachedLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var playbackSpeed by remember(mediaKey) { mutableStateOf(1.0f) }
    var aspectRatio by remember(mediaKey) { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var startedVideo by remember(mediaKey, streamUrl) { mutableStateOf(false) }
    var showStartupSpinner by remember(mediaKey, streamUrl) { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var movingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }
    var badDuration by remember(mediaKey) { mutableStateOf(false) }
    var seekTargetMs by remember(mediaKey) { mutableStateOf<Long?>(null) }
    var seekStartedMs by remember(mediaKey) { mutableLongStateOf(0L) }
    var seekNotice by remember(mediaKey) { mutableStateOf<String?>(null) }
    var lastHeldAtMs by remember(mediaKey) { mutableLongStateOf(0L) }
    var lastControlActivity by remember(mediaKey) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
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

    fun revealControls() {
        controlsVisible = true
        lastControlActivity = SystemClock.elapsedRealtime()
    }
    fun seekToRequest(timeMs: Long) {
        val target = VodSeekPolicy.target(timeMs, durationMs, badDuration)
        seekTargetMs = target
        seekStartedMs = SystemClock.elapsedRealtime()
        seekNotice = null
        lastControlActivity = seekStartedMs
        runCatching { player.setTime(target) }.onFailure {
            seekTargetMs = null
            seekNotice = "VLC could not send this seek request."
        }
    }
    fun stepSeek(deltaMs: Long) {
        seekToRequest((seekTargetMs ?: positionMs) + deltaMs)
    }

    // First Back dismisses the transport even when paused or buffering.
    BackHandler { if (controlsVisible) controlsVisible = false else onBack() }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(80)
            runCatching { seekFocus.requestFocus() }
        }
    }
    LaunchedEffect(controlsVisible, lastControlActivity, playing, buffering,
        movingSlider, seekTargetMs, controlsTimeout) {
        if (controlsVisible && playing && !buffering && !movingSlider &&
            seekTargetMs == null && controlsTimeout > 0) {
            delay(controlsTimeout * 1_000L)
            controlsVisible = false
        }
    }

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
                scope.launch {
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
                            errorText = "VLC could not play this stream. It may be a provider, network, or codec issue."
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
            val realPosition = p.coerceAtLeast(0L)
            if (VodSeekPolicy.contradictsDuration(d, realPosition)) badDuration = true
            positionMs = realPosition
            durationMs = if (badDuration) 0L else d.coerceAtLeast(0L)
            val target = seekTargetMs
            if (target != null) {
                if (VodSeekPolicy.acknowledged(realPosition, target) && !buffering) {
                    seekTargetMs = null
                } else if (SystemClock.elapsedRealtime() - seekStartedMs > 18_000L) {
                    seekTargetMs = null
                    seekNotice = "Seek not confirmed by VLC. This stream may not be seekable."
                }
            }
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
            // Stop native callbacks before releasing the surface. No background native teardown.
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching {
                if (player.vlcVout.areViewsAttached()) player.detachViews()
            }
            attachedLayout = null
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && !controlsVisible &&
                    event.key != Key.Back && event.key != Key.Escape) {
                    revealControls()
                    true
                } else false
            }
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clickable {
                if (controlsVisible) controlsVisible = false else revealControls()
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        errorText = null
                        buffering = true
                        runCatching { player.stop(); player.play() }.onFailure {
                            errorText = "VLC retry failed."
                            buffering = false
                        }
                    }) { Text("Retry VLC") }
                    Button(onClick = ::openExternal) {
                        Text("External player", maxLines = 1)
                    }
                }
            }
        }
        if (controlsVisible) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to library", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text(title, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayTime(positionMs), color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = (if (movingSlider) sliderValue else positionMs.toFloat())
                            .coerceIn(0f, VodSeekPolicy.displayMaximum(
                                durationMs, positionMs, badDuration).toFloat()),
                        onValueChange = {
                            sliderValue = it
                            movingSlider = true
                            revealControls()
                        },
                        onValueChangeFinished = {
                            seekToRequest(sliderValue.toLong())
                            movingSlider = false
                        },
                        valueRange = 0f..VodSeekPolicy.displayMaximum(
                            durationMs, positionMs, badDuration).toFloat(),
                        modifier = Modifier.weight(1f)
                            .focusRequester(seekFocus)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown ||
                                    (event.key != Key.DirectionLeft && event.key != Key.DirectionRight)) {
                                    false
                                } else {
                                    val repeat = event.nativeKeyEvent.repeatCount
                                    val now = SystemClock.elapsedRealtime()
                                    if (repeat == 0 || now - lastHeldAtMs >= 500L) {
                                        val delta = VodSeekPolicy.remoteStep(repeat) *
                                            if (event.key == Key.DirectionLeft) -1 else 1
                                        stepSeek(delta)
                                        lastHeldAtMs = now
                                    }
                                    revealControls()
                                    true
                                }
                            },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (durationMs > 0L) displayTime(durationMs) else "—", color = Color.White)
                }
                if (seekTargetMs != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp).height(20.dp),
                            color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Seeking and buffering…", color = Color.White)
                    }
                }
                seekNotice?.let {
                    Text(it, color = Color(0xFFFFD48C),
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        stepSeek(-VodSeekPolicy.SHORT_STEP_MS)
                    }) {
                        Icon(Icons.Filled.FastRewind, contentDescription = "Back 10 seconds", tint = Color.White)
                    }
                    IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play", tint = Color.White)
                    }
                    IconButton(onClick = {
                        stepSeek(VodSeekPolicy.SHORT_STEP_MS)
                    }) {
                        Icon(Icons.Filled.FastForward, contentDescription = "Forward 10 seconds", tint = Color.White)
                    }
                    if (mediaKey.startsWith("ep:")) {
                        IconButton(onClick = onEpisodes) {
                            Icon(Icons.Filled.List, contentDescription = "Episodes", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        aspectRatio = when (aspectRatio) {
                            null -> "16:9"
                            "16:9" -> "4:3"
                            else -> null
                        }
                        runCatching { player.setAspectRatio(aspectRatio) }
                        revealControls()
                    }) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White)
                    }
                    IconButton(onClick = {
                        playbackSpeed = when (playbackSpeed) {
                            1f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2f
                            else -> 1f
                        }
                        runCatching { player.setRate(playbackSpeed) }
                        revealControls()
                    }) {
                        Icon(Icons.Filled.Speed, contentDescription = "Speed ${playbackSpeed}×", tint = Color.White)
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
