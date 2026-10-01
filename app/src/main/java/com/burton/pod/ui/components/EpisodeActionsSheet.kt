package com.burton.pod.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.burton.pod.ui.theme.BurtonDanger
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute

data class EpisodeAction(
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun EpisodeActionsSheet(
    title: String,
    subtitle: String? = null,
    actions: List<EpisodeAction>,
    onDismiss: () -> Unit,
) {
    BurtonModalSheet(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = BurtonIvory)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = BurtonMute)
        }
        Spacer(Modifier.height(12.dp))
        actions.forEach { action ->
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (action.danger) BurtonDanger else BurtonIvory,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        action.onClick()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
            )
        }
    }
}
