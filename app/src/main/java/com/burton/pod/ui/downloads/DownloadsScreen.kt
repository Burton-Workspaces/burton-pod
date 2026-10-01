package com.burton.pod.ui.downloads

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
import com.burton.pod.ui.theme.BurtonDanger
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonSand

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val rows = snapshot.downloads.values
        .filter { it.status != DownloadStatus.None }
        .sortedByDescending { download ->
            snapshot.episodes.values.flatten().firstOrNull { it.id == download.episodeId }?.publishedAt ?: 0L
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineLarge, color = BurtonIvory)
        Spacer(Modifier.height(16.dp))
        if (rows.isEmpty()) {
            Text(
                "Open a show and tap Download on an episode. Files stay in app storage until you remove them.",
                color = BurtonMute,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.episodeId }) { download ->
                    val episode = snapshot.episodes.values.flatten().firstOrNull { it.id == download.episodeId }
                    DownloadRow(
                        episode = episode,
                        podcastTitle = episode?.let { snapshot.podcast(it.podcastId)?.title },
                        download = download,
                        onPlay = { viewModel.play(download.episodeId) },
                        onDelete = { viewModel.delete(download.episodeId) },
                        onRetry = { viewModel.retry(download.episodeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    episode: Episode?,
    podcastTitle: String?,
    download: Download,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .clickable(enabled = download.status == DownloadStatus.Done, onClick = onPlay)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlbumArt(url = episode?.artworkUrl, size = 52.dp, corner = 10.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode?.title ?: "Episode",
                    style = MaterialTheme.typography.titleMedium,
                    color = BurtonIvory,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        podcastTitle,
                        formatDuration(episode?.durationSeconds).ifBlank { null },
                        statusLabel(download),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (download.status == DownloadStatus.Failed) BurtonDanger else BurtonMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (download.status == DownloadStatus.Downloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { download.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = BurtonSand,
                trackColor = BurtonMute.copy(alpha = 0.25f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when (download.status) {
                DownloadStatus.Failed -> TextButton(onClick = onRetry) { Text("Retry", color = BurtonSand) }
                DownloadStatus.Done, DownloadStatus.Downloading, DownloadStatus.Queued ->
                    TextButton(onClick = onDelete) { Text("Remove", color = BurtonMute) }
                DownloadStatus.None -> Unit
            }
        }
    }
}

private fun statusLabel(download: Download): String = when (download.status) {
    DownloadStatus.Downloading -> "${(download.progress * 100).toInt()}%"
    DownloadStatus.Queued -> "Queued"
    DownloadStatus.Done -> "Downloaded"
    DownloadStatus.Failed -> download.error ?: "Failed"
    DownloadStatus.None -> ""
}
