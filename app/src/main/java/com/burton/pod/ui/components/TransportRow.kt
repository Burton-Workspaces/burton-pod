package com.burton.pod.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.burton.pod.ui.theme.BurtonIvory

@Composable
fun TransportRow(
    isPlaying: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(64.dp)) {
            Icon(
                Icons.Rounded.Replay10,
                contentDescription = "Back 10 seconds",
                tint = BurtonIvory,
                modifier = Modifier.size(44.dp),
            )
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(96.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                contentDescription = "Play or pause",
                tint = BurtonIvory,
                modifier = Modifier.size(88.dp),
            )
        }
        IconButton(onClick = onForward, modifier = Modifier.size(64.dp)) {
            Icon(
                Icons.Rounded.Forward30,
                contentDescription = "Forward 30 seconds",
                tint = BurtonIvory,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}
