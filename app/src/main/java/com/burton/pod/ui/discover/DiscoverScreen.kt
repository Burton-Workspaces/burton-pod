package com.burton.pod.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.pod.domain.SearchHit
import com.burton.pod.ui.components.AlbumArt
import com.burton.pod.ui.settings.DiscoveryFeedsModal
import com.burton.pod.ui.theme.BurtonCharcoal
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonLine
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonSand

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val subscribed = snapshot.podcasts.map { it.feedUrl }.toSet()
    var showFeeds by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.headlineLarge,
                color = BurtonIvory,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showFeeds = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Discovery feeds", tint = BurtonIvory)
            }
        }
        Spacer(Modifier.height(16.dp))
        SearchField(
            value = ui.query,
            placeholder = "Search podcasts",
            onValueChange = viewModel::onQueryChange,
            onClear = viewModel::clear,
            onSearch = { focusManager.clearFocus() },
        )
        Spacer(Modifier.height(8.dp))
        when {
            ui.loading -> Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BurtonSand)
            }
            ui.query.isNotBlank() && ui.searched && ui.hits.isEmpty() && ui.error == null -> Text(
                "No matching podcasts.",
                color = BurtonMute,
            )
            ui.error != null && ui.hits.isEmpty() -> Text(ui.error ?: "", color = BurtonIvory)
            ui.hits.isEmpty() -> Text(
                "This discovery feed has no podcasts.",
                color = BurtonMute,
            )
            else -> {
                if (ui.error != null) {
                    Text(ui.error ?: "", color = BurtonIvory)
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        ui.hits,
                        key = { hit ->
                            listOfNotNull(hit.feedUrl.takeIf { it.isNotBlank() }, hit.lookupId, hit.title, hit.author)
                                .joinToString("|")
                        },
                    ) { hit ->
                        SearchHitRow(
                            hit = hit,
                            subscribed = hit.feedUrl.isNotBlank() && hit.feedUrl in subscribed,
                            enabled = !ui.adding,
                            onSubscribe = { viewModel.subscribeHit(hit) },
                        )
                    }
                }
            }
        }
    }
    if (showFeeds) {
        DiscoveryFeedsModal(onDismiss = { showFeeds = false })
    }
}

@Composable
private fun SearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BurtonIvory,
            unfocusedTextColor = BurtonIvory,
            focusedBorderColor = BurtonSand,
            unfocusedBorderColor = BurtonLine,
            cursorColor = BurtonIvory,
            focusedPlaceholderColor = BurtonMute,
            unfocusedPlaceholderColor = BurtonMute,
            focusedLeadingIconColor = BurtonSand,
            unfocusedLeadingIconColor = BurtonMute,
            focusedTrailingIconColor = BurtonIvory,
            unfocusedTrailingIconColor = BurtonMute,
        ),
    )
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    subscribed: Boolean,
    enabled: Boolean,
    onSubscribe: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(url = hit.artworkUrl, size = 56.dp, corner = 10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                hit.title,
                style = MaterialTheme.typography.titleMedium,
                color = BurtonIvory,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hit.author,
                style = MaterialTheme.typography.bodyMedium,
                color = BurtonMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onSubscribe, enabled = enabled && !subscribed) {
            Text(if (subscribed) "Added" else "Subscribe", color = if (subscribed) BurtonMute else BurtonSand)
        }
    }
}
