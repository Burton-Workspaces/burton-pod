package com.burton.pod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.burton.pod.data.repository.PodSnapshot
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute

@Composable
fun NowPlayingBar(
    snapshot: PodSnapshot,
    onToggle: () -> Unit,
    onSkipForward: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!snapshot.ready) {
        NowPlayingSkeleton(modifier)
        return
    }
    val episode = snapshot.currentEpisode ?: return
    val show = snapshot.podcast(episode.podcastId)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(
            url = episode.artworkUrl ?: show?.artworkUrl,
            size = 48.dp,
            corner = 8.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                color = BurtonIvory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = show?.title ?: "Burton Pod",
                style = MaterialTheme.typography.bodyMedium,
                color = BurtonMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = if (snapshot.playback.playing) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = "Play or pause",
                tint = BurtonIvory,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onSkipForward, modifier = Modifier.size(52.dp)) {
            Icon(
                imageVector = Icons.Rounded.FastForward,
                contentDescription = "Forward ${snapshot.skipForwardSeconds} seconds",
                tint = BurtonIvory,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
