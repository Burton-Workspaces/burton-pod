package com.burton.pod.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.domain.DiscoveryFeed
import com.burton.pod.ui.components.FullScreenModal
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonLine
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonSand
import com.burton.pod.ui.theme.BurtonVoid

@Composable
fun DiscoveryFeedsModal(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val error by viewModel.feedError.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val canAdd = name.isNotBlank() && url.isNotBlank()
    FullScreenModal(
        onDismiss = onDismiss,
        title = "Discovery feeds",
        actionLabel = "Add this feed",
        actionEnabled = canAdd,
        onAction = {
            focusManager.clearFocus()
            viewModel.addFeed(name, url) {
                name = ""
                url = ""
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Discover uses one catalog at a time. Spotify Top Podcasts is the default.",
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
        Spacer(Modifier.height(16.dp))
        feeds.forEach { feed ->
            FeedChoiceRow(
                feed = feed,
                selected = feed.id == selectedId,
                onSelect = { viewModel.selectFeed(feed.id) },
                onRemove = { viewModel.removeFeed(feed.id) },
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "Add a feed",
            style = MaterialTheme.typography.titleLarge,
            color = BurtonIvory,
        )
        Spacer(Modifier.height(10.dp))
        FeedField(
            value = name,
            placeholder = "Name",
            onValueChange = { name = it },
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(10.dp))
        FeedField(
            value = url,
            placeholder = "https://example.com/podcasts.json",
            onValueChange = { url = it },
            imeAction = ImeAction.Done,
            onDone = {
                if (canAdd) {
                    viewModel.addFeed(name, url) {
                        name = ""
                        url = ""
                    }
                }
            },
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error ?: "", color = BurtonIvory, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FeedChoiceRow(
    feed: DiscoveryFeed,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = BurtonSand,
                unselectedColor = BurtonMute,
            ),
        )
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                feed.name,
                style = MaterialTheme.typography.titleMedium,
                color = BurtonIvory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                feed.url,
                style = MaterialTheme.typography.bodyMedium,
                color = BurtonMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!feed.builtIn) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove ${feed.name}", tint = BurtonMute)
            }
        }
    }
}

@Composable
private fun FeedField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BurtonIvory,
            unfocusedTextColor = BurtonIvory,
            focusedBorderColor = BurtonSand,
            unfocusedBorderColor = BurtonLine,
            cursorColor = BurtonIvory,
            focusedPlaceholderColor = BurtonMute,
            unfocusedPlaceholderColor = BurtonMute,
            focusedContainerColor = BurtonVoid,
            unfocusedContainerColor = BurtonVoid,
        ),
    )
}
