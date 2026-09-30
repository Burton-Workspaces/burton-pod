package com.burton.pod.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.BuildConfig
import com.burton.pod.domain.DownloadStatus
import com.burton.pod.ui.components.FullScreenModal
import com.burton.pod.ui.library.LibraryViewModel
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute

@Composable
fun SettingsModal(
    onDismiss: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val downloaded = snapshot.downloads.values.count { it.status == DownloadStatus.Done }
    FullScreenModal(
        onDismiss = onDismiss,
        title = "Settings",
    ) {
        Spacer(Modifier.height(20.dp))
        SettingsRow(
            title = "Library",
            subtitle = when {
                snapshot.podcasts.isEmpty() -> "No subscribed shows"
                else -> "${snapshot.podcasts.size} shows"
            },
        )
        Spacer(Modifier.height(10.dp))
        SettingsRow(
            title = "Downloads",
            subtitle = if (downloaded == 0) "Nothing stored offline" else "$downloaded episodes on this phone",
        )
        Spacer(Modifier.height(10.dp))
        SettingsRow(
            title = "Burton Pod",
            subtitle = "About",
            trailing = BuildConfig.VERSION_NAME,
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = BurtonIvory)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = BurtonMute)
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyLarge, color = BurtonMute)
        }
    }
}
