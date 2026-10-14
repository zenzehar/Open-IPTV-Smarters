/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage

/**
 * Phone-native live listing. The TV EPG grid remains available on televisions,
 * but never forces a 240dp category rail and 12-hour timeline onto a 360dp phone.
 */
@Composable
fun MobileLiveScreen(
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    search: String = "",
    onSearchChange: (String) -> Unit = {},
    viewModel: ChannelsViewModel = viewModel(),
) {
    val groups by viewModel.visibleCategoryGroups.collectAsState()
    var categoryFilter by remember { mutableStateOf("") }
    val matchingGroups = remember(groups, categoryFilter) {
        groups.filter { it.label.contains(categoryFilter.trim(), ignoreCase = true) }
    }
    val rows by viewModel.rows.collectAsState()
    val selected by viewModel.selectedCategory.collectAsState()
    val favOnly by viewModel.favouritesOnly.collectAsState()
    val channelsPresent by viewModel.channelsPresent.collectAsState()

    Column(Modifier.fillMaxSize().background(Color(0xFF0A1023))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CHANNELS", color = Color.White, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh channels", tint = Color.White)
            }
        }
        OutlinedTextField(
            value = categoryFilter,
            onValueChange = { categoryFilter = it },
            placeholder = { Text("Search live categories") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 7.dp),
        ) {
            item(key = "all") {
                CategoryPill("All", selected == null && !favOnly) { viewModel.selectCategory(null) }
            }
            item(key = "favs") {
                CategoryPill("Favourites", favOnly) { viewModel.selectFavourites() }
            }
            items(matchingGroups, key = { it.key }) { group ->
                CategoryPill(group.label, group.key == selected && !favOnly) { viewModel.selectCategory(group.key) }
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isSyncing || channelsPresent == null) CircularProgressIndicator()
                    else if (!hasSources) Button(onClick = onAddSource) { Text("Add IPTV provider") }
                    else Text(if (search.isNotBlank()) "No matching channels" else "No channels found",
                        color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { it.key.toString() }) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF17213C))
                            .clickable { onPlayChannel(row.primary) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = row.primary.logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.primary.shownName, style = MaterialTheme.typography.titleMedium,
                                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.now?.title ?: "No programme information",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0BCD5), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            row.now?.let { programme ->
                                LinearProgressIndicator(
                                    progress = { programme.progressAt(System.currentTimeMillis()) },
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleFavourite(row) }) {
                            Icon(if (row.primary.favourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "Toggle favourite",
                                tint = if (row.primary.favourite) Color(0xFFFFC45A) else Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFF138EAD) else Color(0xFF25304D))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        maxLines = 1,
    )
}
