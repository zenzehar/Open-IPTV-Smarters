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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val profileId by graph.settings.activeProfileId.collectAsState()
    val scope = rememberCoroutineScope()
    var layout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var movingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }

    val libVlc = remember { LibVLC(context.applicationContext, arrayListOf("--network-caching=1500", "--http-reconnect")) }
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

    LaunchedEffect(layout, streamUrl) {
        val surface = layout ?: return@LaunchedEffect
        try {
            val resumeFrom = graph.playbackPositions.get(profileId, mediaKey)
                ?.takeIf { !it.isFinished }?.positionMillis ?: 0L
            player.attachViews(surface, null, true, false)
            player.setEventListener { event ->
                scope.launch {
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            buffering = false
                            playing = true
                            if (resumeFrom > 0L) {
                                // Only apply a saved position when the stream becomes seekable.
                                runCatching { player.setTime(resumeFrom) }
                            }
                        }
                        MediaPlayer.Event.Paused -> playing = false
                        MediaPlayer.Event.Buffering, MediaPlayer.Event.Opening -> buffering = true
                        MediaPlayer.Event.EncounteredError -> {
                            errorText = "VLC could not decode this stream. Check provider access or try an external player."
                            buffering = false
                        }
                        MediaPlayer.Event.EndReached -> {
                            playing = false
                            buffering = false
                        }
                    }
                }
            }
            val media = Media(libVlc, Uri.parse(streamUrl))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":http-user-agent=${userAgent.replace("\n", "").replace("\r", "")}")
            media.addOption(":network-caching=1500")
            player.media = media
            media.release()
            player.play()
        } catch (e: Exception) {
            errorText = "VLC could not open this stream: ${e.localizedMessage ?: "unknown error"}"
            buffering = false
        }
    }

    LaunchedEffect(mediaKey) {
        while (isActive) {
            delay(1_000L)
            val p = runCatching { player.time }.getOrDefault(0L)
            val d = runCatching { player.length }.getOrDefault(0L)
            positionMs = p.coerceAtLeast(0L)
            durationMs = d.coerceAtLeast(0L)
        }
    }
    LaunchedEffect(mediaKey) {
        while (isActive) {
            delay(15_000L)
            persistPosition()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Use a separate IO scope: composition scope is cancelled on navigation.
            val p = runCatching { player.time }.getOrDefault(0L)
            val d = runCatching { player.length }.getOrDefault(0L)
            if (p > 5_000L && d > 0L) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    graph.playbackPositions.upsert(PlaybackPosition(
                        profileId, mediaKey, p, d, System.currentTimeMillis(),
                    ))
                }
            }
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clickable { controlsVisible = !controlsVisible },
            factory = { ctx -> VLCVideoLayout(ctx).also { layout = it } },
        )
        if (buffering && errorText == null) {
            Text("Opening in VLC player…", color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(16.dp))
        }
        if (errorText != null) {
            Column(
                Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(errorText.orEmpty(), color = Color.White)
                Button(onClick = ::openExternal) { Text("Open in another player") }
            }
        }
        if (controlsVisible) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
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
                        runCatching { player.setTime((player.time - 15_000L).coerceAtLeast(0L)) }
                    }) {
                        Icon(Icons.Filled.FastRewind, contentDescription = "Back 15 seconds", tint = Color.White)
                    }
                    IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play", tint = Color.White)
                    }
                    IconButton(onClick = {
                        runCatching { player.setTime((player.time + 15_000L).coerceAtMost(durationMs.coerceAtLeast(0L))) }
                    }) {
                        Icon(Icons.Filled.FastForward, contentDescription = "Forward 15 seconds", tint = Color.White)
                    }
                    IconButton(onClick = ::openExternal) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "External player", tint = Color.White)
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
