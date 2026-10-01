package com.burton.pod.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute

@Composable
fun TransportRow(
    isPlaying: Boolean,
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    onBack: () -> Unit,
    onBackLongPress: () -> Unit,
    onToggle: () -> Unit,
    onForward: () -> Unit,
    onForwardLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipButton(
            seconds = skipBackSeconds,
            rewind = true,
            onClick = onBack,
            onLongClick = onBackLongPress,
        )
        IconButton(onClick = onToggle, modifier = Modifier.size(96.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                contentDescription = "Play or pause",
                tint = BurtonIvory,
                modifier = Modifier.size(88.dp),
            )
        }
        SkipButton(
            seconds = skipForwardSeconds,
            rewind = false,
            onClick = onForward,
            onLongClick = onForwardLongPress,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkipButton(
    seconds: Int,
    rewind: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val label = if (rewind) "Back $seconds seconds" else "Forward $seconds seconds"
    Column(
        modifier = Modifier
            .size(width = 72.dp, height = 72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (rewind) Icons.Rounded.Replay else Icons.Rounded.FastForward,
            contentDescription = label,
            tint = BurtonIvory,
            modifier = Modifier.size(40.dp),
        )
        Text(
            "${seconds}s",
            style = MaterialTheme.typography.labelLarge,
            color = BurtonMute,
        )
    }
}
