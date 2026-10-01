package com.burton.pod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.burton.pod.ui.theme.BurtonElevated
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.LocalGrayscaleArtwork
import com.burton.pod.ui.theme.artworkColorFilter

@Composable
fun AlbumArt(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    corner: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(corner)
    val grayscale = LocalGrayscaleArtwork.current
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(BurtonElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Podcasts,
                contentDescription = null,
                tint = BurtonMute,
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = artworkColorFilter(grayscale),
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(BurtonElevated),
        )
    }
}
