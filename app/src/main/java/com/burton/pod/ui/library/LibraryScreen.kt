package com.burton.pod.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.domain.Podcast
import com.burton.pod.ui.components.AlbumArt
import com.burton.pod.ui.components.RoomsSkeleton
import com.burton.pod.ui.settings.SettingsModal
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute

@Composable
fun LibraryScreen(
    onOpenShow: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                color = BurtonIvory,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::refreshAll, enabled = snapshot.podcasts.isNotEmpty()) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh feeds", tint = BurtonIvory)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = BurtonIvory)
            }
        }
        Text(
            text = when {
                !snapshot.ready -> "Restoring your shows"
                snapshot.podcasts.isEmpty() -> "Subscribe from Discover, or paste an RSS address"
                snapshot.refreshing.isNotEmpty() -> "Refreshing ${snapshot.refreshing.size} feeds"
                else -> "${snapshot.podcasts.size} shows"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
        Spacer(Modifier.height(16.dp))
        when {
            !snapshot.ready -> RoomsSkeleton()
            snapshot.podcasts.isEmpty() -> Text(
                "Nothing subscribed yet. Open Discover to search the podcast directory or add a feed URL.",
                color = BurtonMute,
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(snapshot.podcasts, key = { it.id }) { podcast ->
                        PodcastCard(
                            podcast = podcast,
                            episodeCount = snapshot.episodesFor(podcast.id).size,
                            refreshing = podcast.id in snapshot.refreshing,
                            onClick = { onOpenShow(podcast.id) },
                        )
                    }
                }
            }
        }
    }
    if (showSettings) {
        SettingsModal(onDismiss = { showSettings = false })
    }
}

@Composable
private fun PodcastCard(
    podcast: Podcast,
    episodeCount: Int,
    refreshing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(url = podcast.artworkUrl, size = 72.dp, corner = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                podcast.title,
                style = MaterialTheme.typography.titleLarge,
                color = BurtonIvory,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    podcast.author.ifBlank { null },
                    if (refreshing) "Refreshing" else "$episodeCount episodes",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = BurtonMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
