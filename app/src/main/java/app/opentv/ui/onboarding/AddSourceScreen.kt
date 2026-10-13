/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.onboarding

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.isRunningOnTelevision
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.channels.readClipboardText

/**
 * First-run setup.
 *
 * Two things this screen does that most IPTV players do not:
 *
 * 1. **Test before saving.** The user finds out the password is wrong here, with a sentence
 *    explaining what to change, rather than staring at an empty channel list later.
 * 2. **Says where the credentials go.** People are handing over their provider login. They
 *    are entitled to know it stays on the device, and to be told so without having to go
 *    looking for a privacy policy.
 */
@Composable
fun AddSourceScreen(
    viewModel: SourcesViewModel,
    onFinished: () -> Unit,
    editingSourceId: Long? = null,
) {
    val context = LocalContext.current
    // The QR "set it up from your phone" flow only makes sense on a TV — it serves a setup page
    // FROM this device for a phone to fill in. On a phone that same screen shows a QR meant to be
    // scanned BY a phone, which is nonsensical and traps the user — so a non-TV device defaults
    // straight to the direct entry form. On a TV the QR is still offered first, because typing a
    // server address and password with a d-pad is the worst moment in every app of this kind.
    val isTelevision = remember(context) { isRunningOnTelevision(context) }
    // Editing an existing provider always uses the direct form (the fields are pre-filled, and there
    // is nothing to pair); only a fresh add on a TV offers the phone-pairing QR first.
    var usePhone by remember { mutableStateOf(false) }

    if (usePhone) {
        PhonePairingScreen(
            // Even on a failed sync, leave the pairing screen: the source is already saved, so the
            // home screen shows either the guide (success) or a recoverable error (failure) rather
            // than stranding the user on an endless "Got it — connecting…" spinner.
            onReceived = { draft -> viewModel.saveAndSync(draft) { onFinished() } },
            onCancel = { usePhone = false },
        )
        return
    }

    val ui by viewModel.ui.collectAsState()

    // When editing, find the source being edited and pre-fill every field from it. Keyed on its id
    // so the fields seed once the list has loaded; a fresh add leaves everything blank.
    val existing = remember(editingSourceId, ui.sources) {
        editingSourceId?.let { id -> ui.sources.firstOrNull { it.id == id } }
    }

    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: SourceKind.XTREAM) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var url by remember(existing?.id) { mutableStateOf(existing?.url ?: "") }
    var username by remember(existing?.id) { mutableStateOf(existing?.username ?: "") }
    var password by remember(existing?.id) { mutableStateOf(existing?.password ?: "") }
    var mac by remember(existing?.id) { mutableStateOf(existing?.macAddress ?: "") }
    var epgUrl by remember(existing?.id) { mutableStateOf(existing?.epgUrl ?: "") }
    var showAdvanced by remember { mutableStateOf(false) }
    var revealPassword by remember { mutableStateOf(false) }
    var userAgent by remember(existing?.id) { mutableStateOf(existing?.userAgent ?: Source.DEFAULT_USER_AGENT) }

    // Editing copies onto the existing row, so id, enabled state, live format and last-sync stamp
    // are preserved; a fresh add starts from a blank Source (id 0, so save() inserts).
    fun draft(): Source = (existing ?: Source(name = "", kind = kind, url = "")).copy(
        name = name.ifBlank {
            if (kind == SourceKind.M3U) context.getString(R.string.onboarding_default_playlist_name)
            else context.getString(R.string.onboarding_default_provider_name)
        },
        kind = kind,
        url = url,
        username = username.takeIf { it.isNotBlank() },
        password = password.takeIf { it.isNotBlank() },
        macAddress = mac.takeIf { it.isNotBlank() },
        epgUrl = epgUrl.takeIf { it.isNotBlank() },
        userAgent = userAgent.ifBlank { Source.DEFAULT_USER_AGENT },
    )

    val canSubmit = url.isNotBlank() && when (kind) {
        SourceKind.XTREAM -> username.isNotBlank() && password.isNotBlank()
        SourceKind.M3U -> true
        SourceKind.STALKER -> mac.isNotBlank()
    }

    // Matches the reference's spatial hierarchy, palette and field proportions.
    // The artwork and wordmark are original MyIPTV elements, not copied brand assets.
    val rose = Color(0xFFEBC3D3)
    val ink = Color(0xFF271B2A)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = rose,
        unfocusedContainerColor = rose.copy(alpha = 0.94f),
        focusedTextColor = ink,
        unfocusedTextColor = ink,
        focusedPlaceholderColor = ink.copy(alpha = 0.65f),
        unfocusedPlaceholderColor = ink.copy(alpha = 0.65f),
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
        cursorColor = ink,
        focusedLeadingIconColor = ink,
        unfocusedLeadingIconColor = ink,
        focusedTrailingIconColor = ink,
        unfocusedTrailingIconColor = ink,
    )
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.horizontalGradient(
                listOf(Color(0xFF4D30D2), Color(0xFF9D48B6), Color(0xFFEF7384)),
            ))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth >= 780.dp
        val fieldHeight = if (wide) 66.dp else 56.dp
        val fieldFont = if (wide) 22.sp else 17.sp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(
                    Modifier.weight(0.46f).fillMaxHeight().padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Large graphic wordmark, intentionally independent of Smarters assets.
                    Icon(Icons.Filled.Tv, contentDescription = null,
                        modifier = Modifier.padding(16.dp), tint = Color.White)
                    Text("MyIPTV", style = MaterialTheme.typography.displayLarge,
                        color = Color.White, fontWeight = FontWeight.Light,
                        maxLines = 1)
                    Text("SMART TV PLAYER", style = MaterialTheme.typography.titleLarge,
                        color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 1)
                }
            }
            Column(
                modifier = Modifier.weight(if (wide) 0.54f else 1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 28.dp else 22.dp, vertical = if (wide) 30.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 10.dp),
            ) {
                if (!wide) {
                    Text("MyIPTV", color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (existing != null) "Edit Your Login Details" else "Enter Your Login Details",
                    style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Spacer(Modifier.height(if (wide) 12.dp else 6.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = TextStyle(fontSize = fieldFont, color = ink),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = fieldHeight),
                )
                if (kind == SourceKind.XTREAM) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = { Text("Username") },
                        singleLine = true,
                        colors = fieldColors,
                        textStyle = TextStyle(fontSize = fieldFont, color = ink),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = fieldHeight),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (revealPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { revealPassword = !revealPassword }) {
                                Icon(if (revealPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (revealPassword) "Hide password" else "Show password",
                                    tint = ink)
                            }
                        },
                        colors = fieldColors,
                        textStyle = TextStyle(fontSize = fieldFont, color = ink),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = fieldHeight),
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = {
                        Text(if (kind == SourceKind.M3U) "M3U playlist URL"
                            else if (kind == SourceKind.STALKER) "Portal URL"
                            else "http://server-address:port")
                    },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = TextStyle(fontSize = fieldFont, color = ink),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = fieldHeight),
                )
                if (kind == SourceKind.STALKER) {
                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        placeholder = { Text("MAC address") },
                        singleLine = true,
                        colors = fieldColors,
                        textStyle = TextStyle(fontSize = fieldFont, color = ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = { viewModel.saveAndSync(draft()) { ok -> if (ok) onFinished() } },
                    enabled = canSubmit && !ui.testing && !ui.syncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1E1826),
                        disabledContainerColor = Color.White.copy(alpha = 0.58f),
                        disabledContentColor = Color(0xFF423943),
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp),
                ) {
                    if (ui.syncing) CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp).height(19.dp),
                        color = ink, strokeWidth = 2.dp,
                    ) else Text(if (existing == null) "ADD USER" else "SAVE USER",
                        fontSize = if (wide) 22.sp else 17.sp, fontWeight = FontWeight.Medium)
                }
                if (ui.sources.isNotEmpty()) {
                    TextButton(onClick = onFinished, enabled = !ui.syncing) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text("LIST USERS", color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        SourceKind.XTREAM to "Xtream",
                        SourceKind.M3U to "M3U",
                        SourceKind.STALKER to "Stalker",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = kind == value,
                            onClick = { kind = value },
                            label = { Text(label, maxLines = 1) },
                            modifier = Modifier.padding(end = 5.dp),
                        )
                    }
                }
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showAdvanced) "HIDE ADVANCED SETTINGS" else "ADVANCED SETTINGS",
                        color = Color.White)
                }
                if (showAdvanced) {
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        placeholder = { Text("Optional EPG URL") },
                        colors = fieldColors,
                        textStyle = TextStyle(color = ink),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = userAgent,
                        onValueChange = { userAgent = it },
                        placeholder = { Text("User-Agent") },
                        colors = fieldColors,
                        textStyle = TextStyle(color = ink),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                ui.testResult?.let { Text(it, color = Color.White) }
                ui.testError?.let { Text(it, color = Color.White, fontWeight = FontWeight.Bold) }
                ui.syncMessage?.let { Text(it, color = Color.White) }
                OutlinedButton(
                    onClick = { viewModel.test(draft()) },
                    enabled = canSubmit && !ui.syncing && !ui.testing,
                ) { Text(if (ui.testing) "Checking…" else "TEST CONNECTION", color = Color.White) }
                if (isTelevision) {
                    OutlinedButton(onClick = { usePhone = true }) {
                        Text("CONNECT FROM PHONE", color = Color.White)
                    }
                }
                Text(
                    stringResource(R.string.onboarding_privacy_note),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * A trailing "paste" affordance for a provider field: reads the system clipboard and appends it
 * via [onPaste]. A long Xtream URL or username is miserable to type on a d-pad, so this is exactly
 * what the reporter asked for. Reads the same clipboard as the on-screen keyboard's Paste key; an
 * empty clipboard just shows a brief message rather than doing nothing silently.
 */
@Composable
private fun PasteButton(onPaste: (String) -> Unit) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val pasted = readClipboardText(context)
            if (pasted != null) onPaste(pasted)
            else Toast.makeText(context, context.getString(R.string.kbd_clipboard_empty), Toast.LENGTH_SHORT).show()
        },
    ) {
        Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.kbd_paste))
    }
}
