package com.burton.pod.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.domain.PlaybackOptions
import com.burton.pod.domain.formatClock
import com.burton.pod.domain.formatDuration
import com.burton.pod.domain.formatSpeed
import com.burton.pod.domain.showNotesUrl
import com.burton.pod.domain.stripHtml
import com.burton.pod.ui.components.AlbumArt
import com.burton.pod.ui.components.BurtonModalSheet
import com.burton.pod.ui.components.TransportRow
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonSand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val uriHandler = LocalUriHandler.current
    val notesUrl = episode?.showNotesUrl()
    val favorited = episode?.id in snapshot.favoriteIds
    var showSpeed by remember { mutableStateOf(false) }
    var skipPicker by remember { mutableStateOf<SkipPicker?>(null) }
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
            Text(
                formatSpeed(snapshot.playbackSpeed),
                style = MaterialTheme.typography.titleMedium,
                color = BurtonSand,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = { showSpeed = true })
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
            IconButton(
                onClick = viewModel::toggleFavorite,
                enabled = episode != null,
            ) {
                Icon(
                    imageVector = if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorited) "Remove from favorites" else "Add to favorites",
                    tint = if (favorited) BurtonSand else BurtonIvory,
                )
            }
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
        episodeMeta(episode?.publishedAt, episode?.durationSeconds)?.let { meta ->
            Spacer(Modifier.height(4.dp))
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = BurtonMute, textAlign = TextAlign.Center)
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
                skipBackSeconds = snapshot.skipBackSeconds,
                skipForwardSeconds = snapshot.skipForwardSeconds,
                onBack = viewModel::skipBack,
                onBackLongPress = { skipPicker = SkipPicker.Back },
                onToggle = viewModel::toggle,
                onForward = viewModel::skipForward,
                onForwardLongPress = { skipPicker = SkipPicker.Forward },
                modifier = Modifier.weight(3f),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::next) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next episode", tint = BurtonIvory)
            }
        }
        if (notesUrl != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .background(BurtonCharcoal, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { uriHandler.openUri(notesUrl) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = BurtonSand)
                Text("Show Notes", style = MaterialTheme.typography.titleMedium, color = BurtonIvory)
            }
        }
        val episodeNotes = episode?.description?.let(::stripHtml).orEmpty()
        val showBlurb = podcast?.description?.let(::stripHtml).orEmpty()
        if (showBlurb.isNotBlank() || episodeNotes.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BurtonCharcoal, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Text("About", style = MaterialTheme.typography.titleLarge, color = BurtonIvory)
                if (showBlurb.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        showBlurb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BurtonMute,
                    )
                }
                if (episodeNotes.isNotBlank() && episodeNotes != showBlurb) {
                    Spacer(Modifier.height(14.dp))
                    Text("This episode", style = MaterialTheme.typography.titleMedium, color = BurtonIvory)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        episodeNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BurtonMute,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
    if (showSpeed) {
        SpeedSheet(
            speed = snapshot.playbackSpeed,
            saved = snapshot.savedSpeeds,
            onSpeed = viewModel::setSpeed,
            onSave = viewModel::saveSpeedPreset,
            onRemoveSaved = viewModel::removeSpeedPreset,
            onDismiss = { showSpeed = false },
        )
    }
    skipPicker?.let { picker ->
        SkipSheet(
            title = if (picker == SkipPicker.Back) "Skip back" else "Skip forward",
            selected = if (picker == SkipPicker.Back) snapshot.skipBackSeconds else snapshot.skipForwardSeconds,
            onSelect = { seconds ->
                if (picker == SkipPicker.Back) {
                    viewModel.setSkipBack(seconds)
                } else {
                    viewModel.setSkipForward(seconds)
                }
                skipPicker = null
            },
            onDismiss = { skipPicker = null },
        )
    }
}

private enum class SkipPicker { Back, Forward }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedSheet(
    speed: Float,
    saved: List<Float>,
    onSpeed: (Float) -> Unit,
    onSave: () -> Unit,
    onRemoveSaved: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = (PlaybackOptions.speeds + saved).distinct().sorted()
    val savedSet = saved.toSet()
    BurtonModalSheet(onDismiss = onDismiss) {
        Text("Playback speed", style = MaterialTheme.typography.titleLarge, color = BurtonIvory)
        Spacer(Modifier.height(6.dp))
        Text(
            "Long press a saved speed to remove it. Save the current one for later.",
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
        Spacer(Modifier.height(16.dp))
        Text(formatSpeed(speed), style = MaterialTheme.typography.headlineMedium, color = BurtonSand)
        Slider(
            value = speed,
            onValueChange = onSpeed,
            valueRange = PlaybackOptions.minSpeed..PlaybackOptions.maxSpeed,
            steps = 49,
            colors = SliderDefaults.colors(
                thumbColor = BurtonIvory,
                activeTrackColor = BurtonSand,
                inactiveTrackColor = BurtonMute.copy(alpha = 0.3f),
            ),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { value ->
                ChoiceChip(
                    label = formatSpeed(value),
                    selected = value == speed,
                    onClick = { onSpeed(value) },
                    onLongClick = { if (value in savedSet) onRemoveSaved(value) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (speed in savedSet) "Saved for later" else "Save ${formatSpeed(speed)}",
            style = MaterialTheme.typography.titleMedium,
            color = if (speed in savedSet) BurtonMute else BurtonSand,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = speed !in savedSet, role = Role.Button, onClick = onSave)
                .padding(vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkipSheet(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BurtonModalSheet(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = BurtonIvory)
        Spacer(Modifier.height(6.dp))
        Text("Sets how far each tap jumps.", style = MaterialTheme.typography.bodyMedium, color = BurtonMute)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaybackOptions.skipSeconds.forEach { seconds ->
                ChoiceChip(
                    label = "${seconds}s",
                    selected = seconds == selected,
                    onClick = { onSelect(seconds) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) BurtonSand else BurtonIvory,
        modifier = Modifier
            .background(
                if (selected) BurtonSand.copy(alpha = 0.16f) else BurtonCharcoal,
                RoundedCornerShape(14.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private fun episodeMeta(publishedAt: Long?, durationSeconds: Long?): String? {
    val parts = listOfNotNull(
        publishedAt?.takeIf { it > 0L }?.let { SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(it)) },
        formatDuration(durationSeconds).ifBlank { null },
    )
    return parts.joinToString(" · ").ifBlank { null }
}
