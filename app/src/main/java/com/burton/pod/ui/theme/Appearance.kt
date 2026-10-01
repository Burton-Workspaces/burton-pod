package com.burton.pod.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

val LocalGrayscaleArtwork = compositionLocalOf { false }

fun artworkColorFilter(grayscale: Boolean): ColorFilter? =
    if (grayscale) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    } else {
        null
    }
