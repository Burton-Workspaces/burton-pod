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
    val linkUrl: String? = null,
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
    val speed: Float = 1f,
)

object PlaybackOptions {
    val speeds = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f)
    val skipSeconds = listOf(5, 10, 15, 30, 45, 60)
    const val minSpeed = 0.5f
    const val maxSpeed = 3.0f
    const val defaultSkipBack = 10
    const val defaultSkipForward = 30
}

fun roundSpeed(speed: Float): Float {
    val clamped = speed.coerceIn(PlaybackOptions.minSpeed, PlaybackOptions.maxSpeed)
    return kotlin.math.round(clamped * 20f) / 20f
}

fun formatSpeed(speed: Float): String {
    val value = roundSpeed(speed)
    val text = if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
    return "${text}×"
}

fun normalizeSkipSeconds(seconds: Int, fallback: Int): Int =
    if (seconds in PlaybackOptions.skipSeconds) seconds else fallback

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (isEmpty()) return this
    val start = from.coerceIn(indices)
    val end = to.coerceIn(indices)
    if (start == end) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(start)
    mutable.add(end, item)
    return mutable
}

fun stripHtml(html: String): String {
    if (html.isBlank()) return ""
    return html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n")
        .replace(Regex("(?i)</div>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex("\\n[ \\t]*"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

fun extractHttpUrl(text: String): String? {
    val match = Regex("https?://[^\\s<>\"']+").find(text) ?: return null
    return match.value.trimEnd('.', ',', ')', ']', '"', '\'')
}

fun Episode.showNotesUrl(): String? {
    linkUrl?.takeIf { it.startsWith("http") }?.let { return it }
    return extractHttpUrl(description)
}

data class SearchHit(
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val lookupId: String? = null,
) {
    fun discoverIdentity(): String =
        listOfNotNull(
            lookupId?.takeIf { it.isNotBlank() },
            title.trim().lowercase(),
            author.trim().lowercase(),
        ).joinToString("|")

    fun sameDiscoverHit(other: SearchHit): Boolean {
        if (!lookupId.isNullOrBlank() && lookupId == other.lookupId) return true
        if (feedUrl.isNotBlank() && feedUrl == other.feedUrl) return true
        return title == other.title && author == other.author
    }

    fun matchesLibrary(podcasts: Collection<Podcast>): Boolean {
        val url = feedUrl.trim()
        if (url.isNotBlank() && podcasts.any { it.feedUrl.equals(url, ignoreCase = true) }) return true
        val name = title.trim()
        if (name.isBlank()) return false
        return podcasts.any { it.title.equals(name, ignoreCase = true) }
    }
}

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
