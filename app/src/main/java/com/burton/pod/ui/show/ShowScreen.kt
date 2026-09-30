package com.burton.pod.ui.show

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.domain.Download
import com.burton.pod.domain.DownloadStatus
import com.burton.pod.domain.Episode
import com.burton.pod.domain.formatDuration
import com.burton.pod.ui.components.AlbumArt
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonSand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShowScreen(
    onBack: () -> Unit,
    viewModel: ShowViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val podcast = snapshot.podcast(viewModel.podcastId)
    val episodes = snapshot.episodesFor(viewModel.podcastId)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = BurtonIvory)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = BurtonIvory)
            }
        }
        if (podcast == null) {
            Text("This show is no longer in your library.", color = BurtonMute)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                AlbumArt(url = podcast.artworkUrl, size = 92.dp, corner = 16.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(podcast.title, style = MaterialTheme.typography.headlineMedium, color = BurtonIvory)
                    if (podcast.author.isNotBlank()) {
                        Text(podcast.author, style = MaterialTheme.typography.bodyMedium, color = BurtonMute)
                    }
                    TextButton(onClick = {
                        viewModel.unsubscribe()
                        onBack()
                    }) {
                        Text("Unsubscribe", color = BurtonSand)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        download = snapshot.downloadFor(episode.id),
                        playing = snapshot.playback.episodeId == episode.id && snapshot.playback.playing,
                        onPlay = { viewModel.play(episode.id) },
                        onDownload = { viewModel.download(episode.id) },
                        onDelete = { viewModel.deleteDownload(episode.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    download: Download?,
    playing: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (playing) "Playing" else publishedLabel(episode.publishedAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (playing) BurtonSand else BurtonMute,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BurtonIvory,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(formatDuration(episode.durationSeconds).ifBlank { null })
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(), style = MaterialTheme.typography.bodyMedium, color = BurtonMute)
                }
            }
            IconButton(
                onClick = {
                    if (download?.status == DownloadStatus.Done) onDelete() else onDownload()
                },
            ) {
                Icon(
                    imageVector = if (download?.status == DownloadStatus.Done) {
                        Icons.Rounded.DownloadDone
                    } else {
                        Icons.Rounded.Download
                    },
                    contentDescription = if (download?.status == DownloadStatus.Done) "Remove download" else "Download",
                    tint = if (download?.status == DownloadStatus.Done) BurtonSand else BurtonIvory,
                )
            }
        }
        if (download?.status == DownloadStatus.Downloading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { download.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = BurtonSand,
                trackColor = BurtonMute.copy(alpha = 0.25f),
            )
        }
    }
}

private fun publishedLabel(epochMs: Long): String {
    if (epochMs <= 0L) return "Episode"
    return SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(epochMs))
}
