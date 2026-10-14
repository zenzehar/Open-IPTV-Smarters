/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.sync.SyncServer
import app.opentv.sync.CloudCredentialVault
import app.opentv.sync.CloudCredentials
import app.opentv.sync.CloudRelaySync
import app.opentv.sync.CloudSyncWorker
import app.opentv.sync.NextcloudLogin
import app.opentv.core.ServiceLocator
import app.opentv.pairing.QrCodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.opentv.ui.SyncViewModel

/**
 * Local watch-history sync between two OpenTV devices on the same wifi. No servers of ours: one
 * device shares behind a code, the other receives. Only continue-watching positions travel.
 */
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = viewModel(),
) {
    var mode by remember { mutableStateOf(Mode.SHARE) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_sync_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.sync_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 720.dp),
        )

        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeChip(stringResource(R.string.sync_share_from_here), mode == Mode.SHARE) { mode = Mode.SHARE }
            ModeChip(stringResource(R.string.sync_receive), mode == Mode.RECEIVE) { mode = Mode.RECEIVE }
            ModeChip(stringResource(R.string.sync_nas), mode == Mode.NAS) { mode = Mode.NAS }
            ModeChip("Cloud drive", mode == Mode.CLOUD) { mode = Mode.CLOUD }
        }
        Spacer(Modifier.height(20.dp))

        when (mode) {
            Mode.SHARE -> SharePane(viewModel)
            Mode.RECEIVE -> ReceivePane(viewModel)
            Mode.NAS -> NasPane(viewModel)
            Mode.CLOUD -> CloudPane()
        }
    }
}

private enum class Mode { SHARE, RECEIVE, NAS, CLOUD }

@Composable
private fun SharePane(viewModel: SyncViewModel) {
    val state by viewModel.serverState.collectAsState()
    Card {
        Column(Modifier.padding(20.dp).widthIn(max = 640.dp)) {
            when (val s = state) {
                is SyncServer.State.Sharing -> {
                    Text(stringResource(R.string.sync_ready_to_share), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sync_on_other_device))
                    Spacer(Modifier.height(12.dp))
                    Field(stringResource(R.string.sync_address), s.session.address)
                    Spacer(Modifier.height(8.dp))
                    Field(stringResource(R.string.sync_code), s.session.code)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sync_type_in_one_go, s.session.address, s.session.code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.stopSharing() }) { Text(stringResource(R.string.sync_stop_sharing)) }
                }
                is SyncServer.State.Failed -> {
                    Text(s.reason, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.startSharing() }) { Text(stringResource(R.string.common_try_again)) }
                }
                else -> {
                    Text(stringResource(R.string.sync_will_share))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.startSharing() }) { Text(stringResource(R.string.sync_start_sharing)) }
                }
            }
        }
    }
}

@Composable
private fun ReceivePane(viewModel: SyncViewModel) {
    val state by viewModel.receiveState.collectAsState()
    var entry by remember { mutableStateOf("") }

    Row {
        Card {
            Column(Modifier.padding(20.dp).widthIn(max = 520.dp)) {
                Text(stringResource(R.string.sync_type_what_shows), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    entry.ifEmpty { "address:port#code" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (entry.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                NumPad(
                    onKey = { if (entry.length < 40) entry += it },
                    onBackspace = { entry = entry.dropLast(1) },
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = entry.contains('#') && state !is SyncViewModel.ReceiveState.Connecting,
                        onClick = {
                            val address = entry.substringBefore('#')
                            val code = entry.substringAfter('#')
                            viewModel.receive(address, code)
                        },
                    ) { Text(stringResource(R.string.sync_connect)) }

                    when (val s = state) {
                        is SyncViewModel.ReceiveState.Connecting -> Text(stringResource(R.string.sync_connecting))
                        is SyncViewModel.ReceiveState.Done -> Text(
                            stringResource(R.string.sync_synced_items, s.merged),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is SyncViewModel.ReceiveState.Failed -> Text(
                            s.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

/**
 * Free, server-less "cloud" sync through the user's own NAS: every device drops a small JSON file
 * into a shared folder on the SMB share and reads the others'. No server, no account. Requires the
 * NAS to be set up under Recording settings first.
 */
@Composable
private fun NasPane(viewModel: SyncViewModel) {
    val nasState by viewModel.nasState.collectAsState()
    val autoSync by viewModel.nasAutoSync.collectAsState()
    val smbHost by viewModel.smbHost.collectAsState()
    val configured = smbHost.isNotBlank()

    Card {
        Column(Modifier.padding(20.dp).widthIn(max = 640.dp)) {
            Text(stringResource(R.string.sync_nas_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sync_nas_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (!configured) {
                Text(
                    stringResource(R.string.sync_nas_needs_setup),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        enabled = nasState !is SyncViewModel.NasState.Syncing,
                        onClick = { viewModel.syncNas() },
                    ) { Text(stringResource(R.string.sync_nas_sync_now)) }

                    when (val s = nasState) {
                        is SyncViewModel.NasState.Syncing ->
                            Text(stringResource(R.string.sync_nas_syncing))
                        is SyncViewModel.NasState.Done ->
                            Text(s.message, color = MaterialTheme.colorScheme.primary)
                        is SyncViewModel.NasState.Failed ->
                            Text(s.message, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sync_nas_auto),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoSync, onCheckedChange = { viewModel.setNasAutoSync(it) })
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text("$label:  ", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun NumPad(onKey: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123", "456", "789", ".0:", "#⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { ch ->
                    NumKey(ch.toString()) {
                        if (ch == '⌫') onBackspace() else onKey(ch.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun NumKey(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(54.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/**
 * Phone-authorised Nextcloud login using Login Flow v2. The QR contains only a short-lived
 * HTTPS browser login URL, never the cloud app password or the family's sync passphrase.
 * The cloud snapshot is AES-GCM encrypted before it reaches the user's Nextcloud storage.
 */
@Composable
private fun CloudPane() {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val vault = remember(context) { CloudCredentialVault(context) }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(vault.load() != null) }
    var server by remember { mutableStateOf("https://") }
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<NextcloudLogin.Pending?>(null) }

    Card {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Encrypted cloud-drive sync", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sync favourites, Continue Watching and saved profiles when your devices " +
                    "aren't online together. Supports Nextcloud HTTPS accounts. " +
                    "Existing local network and NAS sync are unchanged.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (connected) {
                Text("Nextcloud linked on this device", color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !loading, onClick = {
                        loading = true
                        scope.launch {
                            val result = CloudRelaySync(context, graph).sync()
                            status = when (result) {
                                is CloudRelaySync.Result.Success ->
                                    "Cloud sync complete: ${result.merged} merged from " +
                                        "${result.peerDevices} other devices."
                                is CloudRelaySync.Result.Failed -> result.reason
                                CloudRelaySync.Result.NotConfigured -> "Cloud account not configured."
                            }
                            loading = false
                        }
                    }) { Text(if (loading) "Syncing…" else "Sync now") }
                    OutlinedButton(onClick = { confirmDisconnect = true }) {
                        Text("Disconnect cloud")
                    }
                }
                Text("Automatic cloud checks run about every 15 minutes when Android permits.",
                    style = MaterialTheme.typography.bodySmall)
            } else if (pending == null) {
                OutlinedTextField(
                    value = server, onValueChange = { server = it.take(300) },
                    singleLine = true,
                    label = { Text("Nextcloud server (HTTPS)") },
                    modifier = Modifier.widthIn(max = 620.dp),
                )
                OutlinedTextField(
                    value = passphrase, onValueChange = { passphrase = it.take(200) },
                    singleLine = true,
                    label = { Text("Family sync passphrase (12+ characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.widthIn(max = 620.dp),
                )
                Text(
                    "Use the SAME sync passphrase on every phone or TV. Your cloud provider " +
                        "cannot read the encrypted media-history snapshot without it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(enabled = !loading && passphrase.length >= 12 &&
                        server.startsWith("https://"), onClick = {
                    loading = true
                    status = "Contacting Nextcloud…"
                    scope.launch {
                        val login = NextcloudLogin()
                        val session = runCatching { login.begin(server) }
                            .getOrElse {
                                status = "Unable to start Nextcloud login. Check the HTTPS server address."
                                loading = false
                                return@launch
                            }
                        pending = session
                        status = "Scan the QR with your phone, sign in and approve Koky & Sosy TV."
                        var granted = false
                        repeat(180) {
                            delay(5_000)
                            val result = runCatching { login.poll(session) }
                            if (result.isFailure) {
                                status = "Cloud authorisation failed. Check the server or restart login."
                                pending = null
                                loading = false
                                return@launch
                            }
                            val credential = result.getOrNull()
                            if (credential != null) {
                                val save = runCatching {
                                    vault.save(CloudCredentials(
                                        credential.server, credential.username,
                                        credential.appPassword, passphrase,
                                    ))
                                }
                                if (save.isFailure) {
                                    status = "Android secure credential storage was unavailable."
                                    pending = null
                                    loading = false
                                    return@launch
                                }
                                connected = true
                                granted = true
                                pending = null
                                passphrase = ""
                                CloudSyncWorker.schedule(context)
                                CloudSyncWorker.syncNow(context)
                                status = "Authorised. First encrypted cloud sync scheduled."
                                loading = false
                                return@launch
                            }
                        }
                        if (!granted) {
                            pending = null
                            loading = false
                            status = "Cloud login timed out. Please start again."
                        }
                    }
                }) { Text(if (loading) "Connecting…" else "Log in using phone QR") }
            }

            pending?.let { login ->
                val bitmap = remember(login.loginUrl) {
                    QrCodes.render(login.loginUrl, 310)?.asImageBitmap()
                }
                if (bitmap != null) Image(
                    bitmap = bitmap, contentDescription = "Scan with phone to authorise Nextcloud",
                    modifier = Modifier.size(260.dp),
                )
                Text("Open the QR link on your phone and approve access in Nextcloud.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { pending = null; loading = false }) {
                    Text("Cancel login")
                }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (confirmDisconnect) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text("Disconnect cloud sync?") },
        text = { Text("Only this device's cloud authorisation is removed. " +
            "Manual LAN/NAS sync, profiles and local history remain untouched.") },
        confirmButton = {
            TextButton(onClick = {
                vault.clear()
                CloudSyncWorker.schedule(context)
                connected = false
                status = "Cloud disconnected on this device."
                confirmDisconnect = false
            }) { Text("Disconnect") }
        },
        dismissButton = {
            TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
        },
    )
}
