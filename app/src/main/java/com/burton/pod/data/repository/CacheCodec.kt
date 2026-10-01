package com.burton.pod.data.repository

import com.burton.pod.data.parse.TinyJson
import com.burton.pod.data.parse.TinyJson.bool
import com.burton.pod.data.parse.TinyJson.float
import com.burton.pod.data.parse.TinyJson.floatList
import com.burton.pod.data.parse.TinyJson.int
import com.burton.pod.data.parse.TinyJson.objList
import com.burton.pod.data.parse.TinyJson.str
import com.burton.pod.data.parse.TinyJson.strList
import com.burton.pod.domain.Download
import com.burton.pod.domain.DownloadStatus
import com.burton.pod.domain.Episode
import com.burton.pod.domain.PlaybackOptions
import com.burton.pod.domain.Podcast
import com.burton.pod.domain.normalizeSkipSeconds
import com.burton.pod.domain.roundSpeed

object CatalogCodec {
    fun encode(
        podcasts: List<Podcast>,
        episodes: Map<String, List<Episode>>,
        downloads: Map<String, Download>,
        lastEpisodeId: String?,
        lastPositionMs: Long,
        queueIds: List<String> = emptyList(),
        playedIds: Collection<String> = emptyList(),
        favoriteIds: Collection<String> = emptyList(),
        playbackSpeed: Float = 1f,
        savedSpeeds: List<Float> = emptyList(),
        skipBackSeconds: Int = PlaybackOptions.defaultSkipBack,
        skipForwardSeconds: Int = PlaybackOptions.defaultSkipForward,
        grayscaleArtwork: Boolean = false,
    ): String = TinyJson.stringify(
        mapOf(
            "podcasts" to podcasts.map { it.toMap() },
            "episodes" to episodes.values.flatten().map { it.toMap() },
            "downloads" to downloads.values.map { it.toMap() },
            "lastEpisodeId" to lastEpisodeId,
            "lastPositionMs" to lastPositionMs,
            "queueIds" to queueIds,
            "playedIds" to playedIds.toList(),
            "favoriteIds" to favoriteIds.toList(),
            "playbackSpeed" to roundSpeed(playbackSpeed),
            "savedSpeeds" to savedSpeeds.map(::roundSpeed).distinct().sorted(),
            "skipBackSeconds" to skipBackSeconds,
            "skipForwardSeconds" to skipForwardSeconds,
            "grayscaleArtwork" to grayscaleArtwork,
        ),
    )

    fun decode(json: String): CatalogCache {
        if (json.isBlank()) return CatalogCache()
        val root = TinyJson.parseObject(json)
        val podcasts = root.objList("podcasts").map { it.toPodcast() }
        val episodes = root.objList("episodes").map { it.toEpisode() }.groupBy { it.podcastId }
        val downloads = root.objList("downloads").mapNotNull { it.toDownload() }.associateBy { it.episodeId }
        return CatalogCache(
            podcasts = podcasts,
            episodes = episodes,
            downloads = downloads,
            lastEpisodeId = root.str("lastEpisodeId").ifBlank { null },
            lastPositionMs = root["lastPositionMs"].let {
                when (it) {
                    is Number -> it.toLong()
                    is String -> it.toLongOrNull() ?: 0L
                    else -> 0L
                }
            },
            queueIds = root.strList("queueIds").filter { it.isNotBlank() }.distinct(),
            playedIds = root.strList("playedIds").filter { it.isNotBlank() }.toSet(),
            favoriteIds = root.strList("favoriteIds").filter { it.isNotBlank() }.toSet(),
            playbackSpeed = roundSpeed(root.float("playbackSpeed", 1f).takeIf { it > 0f } ?: 1f),
            savedSpeeds = root.floatList("savedSpeeds").map(::roundSpeed).filter { it > 0f }.distinct().sorted(),
            skipBackSeconds = normalizeSkipSeconds(root.int("skipBackSeconds", PlaybackOptions.defaultSkipBack), PlaybackOptions.defaultSkipBack),
            skipForwardSeconds = normalizeSkipSeconds(root.int("skipForwardSeconds", PlaybackOptions.defaultSkipForward), PlaybackOptions.defaultSkipForward),
            grayscaleArtwork = root.bool("grayscaleArtwork"),
        )
    }

    private fun Podcast.toMap() = mapOf(
        "id" to id,
        "feedUrl" to feedUrl,
        "title" to title,
        "author" to author,
        "description" to description,
        "artworkUrl" to artworkUrl,
        "siteUrl" to siteUrl,
    )

    private fun Episode.toMap() = mapOf(
        "id" to id,
        "podcastId" to podcastId,
        "title" to title,
        "description" to description,
        "publishedAt" to publishedAt,
        "durationSeconds" to durationSeconds,
        "enclosureUrl" to enclosureUrl,
        "enclosureType" to enclosureType,
        "artworkUrl" to artworkUrl,
        "linkUrl" to linkUrl,
    )

    private fun Download.toMap() = mapOf(
        "episodeId" to episodeId,
        "status" to status.name,
        "progress" to progress,
        "bytes" to bytes,
        "totalBytes" to totalBytes,
        "path" to path,
        "error" to error,
    )

    private fun Map<String, Any?>.toPodcast() = Podcast(
        id = str("id").ifBlank { str("feedUrl") },
        feedUrl = str("feedUrl"),
        title = str("title"),
        author = str("author"),
        description = str("description"),
        artworkUrl = str("artworkUrl").ifBlank { null },
        siteUrl = str("siteUrl").ifBlank { null },
    )

    private fun Map<String, Any?>.toEpisode() = Episode(
        id = str("id"),
        podcastId = str("podcastId"),
        title = str("title"),
        description = str("description"),
        publishedAt = (this["publishedAt"] as? Number)?.toLong() ?: str("publishedAt").toLongOrNull() ?: 0L,
        durationSeconds = (this["durationSeconds"] as? Number)?.toLong()
            ?: str("durationSeconds").toLongOrNull(),
        enclosureUrl = str("enclosureUrl"),
        enclosureType = str("enclosureType").ifBlank { null },
        artworkUrl = str("artworkUrl").ifBlank { null },
        linkUrl = str("linkUrl").ifBlank { null },
    )

    private fun Map<String, Any?>.toDownload(): Download? {
        val episodeId = str("episodeId")
        if (episodeId.isBlank()) return null
        val status = runCatching { DownloadStatus.valueOf(str("status", DownloadStatus.None.name)) }
            .getOrDefault(DownloadStatus.None)
        return Download(
            episodeId = episodeId,
            status = status,
            progress = (this["progress"] as? Number)?.toFloat() ?: 0f,
            bytes = (this["bytes"] as? Number)?.toLong() ?: 0L,
            totalBytes = (this["totalBytes"] as? Number)?.toLong() ?: 0L,
            path = str("path").ifBlank { null },
            error = str("error").ifBlank { null },
        )
    }
}

data class CatalogCache(
    val podcasts: List<Podcast> = emptyList(),
    val episodes: Map<String, List<Episode>> = emptyMap(),
    val downloads: Map<String, Download> = emptyMap(),
    val lastEpisodeId: String? = null,
    val lastPositionMs: Long = 0L,
    val queueIds: List<String> = emptyList(),
    val playedIds: Set<String> = emptySet(),
    val favoriteIds: Set<String> = emptySet(),
    val playbackSpeed: Float = 1f,
    val savedSpeeds: List<Float> = emptyList(),
    val skipBackSeconds: Int = PlaybackOptions.defaultSkipBack,
    val skipForwardSeconds: Int = PlaybackOptions.defaultSkipForward,
    val grayscaleArtwork: Boolean = false,
)
