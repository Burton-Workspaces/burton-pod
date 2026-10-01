package com.burton.pod.domain

enum class DownloadStatus {
    None,
    Queued,
    Downloading,
    Done,
    Failed,
}

data class Podcast(
    val id: String,
    val feedUrl: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val siteUrl: String?,
)

data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val description: String,
    val publishedAt: Long,
    val durationSeconds: Long?,
    val enclosureUrl: String,
    val enclosureType: String?,
    val artworkUrl: String?,
)

data class Download(
    val episodeId: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val bytes: Long = 0,
    val totalBytes: Long = 0,
    val path: String? = null,
    val error: String? = null,
)

data class PlaybackState(
    val episodeId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

data class SearchHit(
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val lookupId: String? = null,
)

data class DiscoveryFeed(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean = false,
)

object DefaultDiscoveryFeeds {
    const val SPOTIFY_TOP_ID = "spotify-top"

    val all = listOf(
        DiscoveryFeed(
            id = SPOTIFY_TOP_ID,
            name = "Spotify Top Podcasts",
            url = "https://podcastcharts.byspotify.com/api/charts/top-podcasts?region=us",
            builtIn = true,
        ),
        DiscoveryFeed(
            id = "spotify-trending",
            name = "Spotify Trending",
            url = "https://podcastcharts.byspotify.com/api/charts/trending?region=us",
            builtIn = true,
        ),
        DiscoveryFeed(
            id = "apple-top-shows",
            name = "Apple Top Shows",
            url = "https://rss.marketingtools.apple.com/api/v2/us/podcasts/top/25/podcasts.json",
            builtIn = true,
        ),
        DiscoveryFeed(
            id = "apple-top-podcasts",
            name = "Apple Top Podcasts",
            url = "https://itunes.apple.com/us/rss/toppodcasts/limit=25/json",
            builtIn = true,
        ),
    )
}

data class ParsedFeed(
    val podcast: Podcast,
    val episodes: List<Episode>,
)

fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return formatDuration(total).ifBlank { "0:00" }
}
