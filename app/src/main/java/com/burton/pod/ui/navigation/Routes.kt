package com.burton.pod.ui.navigation

import android.net.Uri

object Routes {
    const val LIBRARY = "library"
    const val DISCOVER = "discover"
    const val QUEUE = "queue"
    const val DOWNLOADS = "downloads"
    const val SHOW = "show/{podcastId}"
    const val PLAYER = "player"

    fun show(podcastId: String): String = "show/${Uri.encode(podcastId)}"
}
