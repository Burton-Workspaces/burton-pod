package com.burton.pod.ui.queue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.domain.Episode
import com.burton.pod.domain.formatDuration
import com.burton.pod.ui.components.AlbumArt
import com.burton.pod.ui.components.EpisodeAction
import com.burton.pod.ui.components.EpisodeActionsSheet
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonSand
import kotlin.math.roundToInt

@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val episodes = snapshot.queueEpisodes
    var menuFor by remember { mutableStateOf<Episode?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Text("Queue", style = MaterialTheme.typography.headlineLarge, color = BurtonIvory)
        Spacer(Modifier.height(16.dp))
        if (episodes.isEmpty()) {
            Text(
                "Add episodes from a show with the queue icon, or long press a row. Drag the handle to set the play order.",
                color = BurtonMute,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
                    QueueRow(
                        episode = episode,
                        podcastTitle = snapshot.podcast(episode.podcastId)?.title,
                        playing = snapshot.playback.episodeId == episode.id,
                        played = episode.id in snapshot.playedIds,
                        onPlay = { viewModel.play(episode.id) },
                        onLongPress = { menuFor = episode },
                        onMove = { from, to -> viewModel.move(from, to) },
                        index = index,
                        lastIndex = episodes.lastIndex,
                    )
                }
            }
        }
    }
    menuFor?.let { episode ->
        val played = episode.id in snapshot.playedIds
        val favorited = episode.id in snapshot.favoriteIds
        EpisodeActionsSheet(
            title = episode.title,
            subtitle = snapshot.podcast(episode.podcastId)?.title,
            actions = listOf(
                EpisodeAction("Move to top") { viewModel.moveToTop(episode.id) },
                EpisodeAction("Move to bottom") { viewModel.moveToBottom(episode.id) },
                EpisodeAction(if (played) "Mark as unplayed" else "Mark as played") {
                    viewModel.markPlayed(episode.id, played = !played)
                },
                EpisodeAction("Remove from queue") { viewModel.remove(episode.id) },
                EpisodeAction("Delete download", danger = true) { viewModel.deleteDownload(episode.id) },
                EpisodeAction(if (favorited) "Remove from favorites" else "Add to favorites") {
                    viewModel.toggleFavorite(episode.id)
                },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    episode: Episode,
    podcastTitle: String?,
    playing: Boolean,
    played: Boolean,
    index: Int,
    lastIndex: Int,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowHeight = with(density) { 88.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress,
                role = Role.Button,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(url = episode.artworkUrl, size = 56.dp, corner = 10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when {
                    playing -> "Playing"
                    played -> "Played"
                    else -> podcastTitle ?: "Episode"
                },
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
            formatDuration(episode.durationSeconds).ifBlank { null }?.let { duration ->
                Text(duration, style = MaterialTheme.typography.bodyMedium, color = BurtonMute)
            }
        }
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = "Reorder",
            tint = BurtonMute,
            modifier = Modifier
                .size(36.dp)
                .pointerInput(index, lastIndex, episode.id) {
                    detectDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragCancel = { dragAccum = 0f },
                        onDragEnd = { dragAccum = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragAccum += amount.y
                            val shift = (dragAccum / rowHeight).roundToInt()
                            if (shift != 0) {
                                val target = (index + shift).coerceIn(0, lastIndex)
                                if (target != index) {
                                    onMove(index, target)
                                    dragAccum -= (target - index) * rowHeight
                                }
                            }
                        },
                    )
                },
        )
    }
}
