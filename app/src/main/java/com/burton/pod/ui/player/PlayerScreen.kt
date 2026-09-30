package com.burton.pod.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.domain.formatClock
import com.burton.pod.ui.components.AlbumArt
import com.burton.pod.ui.components.TransportRow
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonSand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val episode = snapshot.currentEpisode
    val podcast = episode?.let { snapshot.podcast(it.podcastId) }
    val playback = snapshot.playback
    val duration = playback.durationMs.takeIf { it > 0 } ?: ((episode?.durationSeconds ?: 0L) * 1000L)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = BurtonIvory)
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = podcast?.title ?: "Now Playing",
            style = MaterialTheme.typography.headlineMedium,
            color = BurtonIvory,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (playback.playing) "Playing" else "Paused",
            style = MaterialTheme.typography.labelLarge,
            color = BurtonSand,
        )
        Spacer(Modifier.height(20.dp))
        AlbumArt(url = episode?.artworkUrl ?: podcast?.artworkUrl, size = 220.dp, corner = 24.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = episode?.title ?: "Nothing playing",
            style = MaterialTheme.typography.titleLarge,
            color = BurtonIvory,
            textAlign = TextAlign.Center,
        )
        if (!podcast?.author.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = podcast?.author.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = BurtonMute,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        Slider(
            value = if (duration > 0) playback.positionMs.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
            onValueChange = { viewModel.seekTo(it.toLong()) },
            valueRange = 0f..(duration.takeIf { it > 0 }?.toFloat() ?: 1f),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = BurtonIvory,
                activeTrackColor = BurtonSand,
                inactiveTrackColor = BurtonMute.copy(alpha = 0.3f),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(formatClock(playback.positionMs), style = MaterialTheme.typography.labelLarge, color = BurtonMute)
            Spacer(Modifier.weight(1f))
            Text(formatClock(duration), style = MaterialTheme.typography.labelLarge, color = BurtonMute)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous episode", tint = BurtonIvory)
            }
            Spacer(Modifier.weight(1f))
            TransportRow(
                isPlaying = playback.playing,
                onBack = viewModel::skipBack,
                onToggle = viewModel::toggle,
                onForward = viewModel::skipForward,
                modifier = Modifier.weight(3f),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::next) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next episode", tint = BurtonIvory)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
