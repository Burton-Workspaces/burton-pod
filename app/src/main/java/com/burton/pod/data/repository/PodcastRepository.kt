package com.burton.pod.data.repository

import com.burton.pod.data.download.EpisodeDownloader
import com.burton.pod.data.parse.RssParser
import com.burton.pod.data.playback.PlayerHolder
import com.burton.pod.data.search.ItunesSearch
import com.burton.pod.domain.Download
import com.burton.pod.domain.DownloadStatus
import com.burton.pod.domain.Episode
import com.burton.pod.domain.PlaybackOptions
import com.burton.pod.domain.PlaybackState
import com.burton.pod.domain.Podcast
import com.burton.pod.domain.SearchHit
import com.burton.pod.domain.moved
import com.burton.pod.domain.normalizeSkipSeconds
import com.burton.pod.domain.roundSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PodSnapshot(
    val podcasts: List<Podcast> = emptyList(),
    val episodes: Map<String, List<Episode>> = emptyMap(),
    val downloads: Map<String, Download> = emptyMap(),
    val playback: PlaybackState = PlaybackState(),
    val lastEpisodeId: String? = null,
    val refreshing: Set<String> = emptySet(),
    val ready: Boolean = false,
    val error: String? = null,
    val queueIds: List<String> = emptyList(),
    val playedIds: Set<String> = emptySet(),
    val favoriteIds: Set<String> = emptySet(),
    val playbackSpeed: Float = 1f,
    val savedSpeeds: List<Float> = emptyList(),
    val skipBackSeconds: Int = PlaybackOptions.defaultSkipBack,
    val skipForwardSeconds: Int = PlaybackOptions.defaultSkipForward,
    val grayscaleArtwork: Boolean = false,
) {
    val currentEpisode: Episode?
        get() {
            val id = playback.episodeId ?: lastEpisodeId
            return id?.let { episode(it) }
        }

    fun podcast(id: String): Podcast? = podcasts.firstOrNull { it.id == id }

    fun episode(id: String): Episode? =
        episodes.values.flatten().firstOrNull { it.id == id }

    fun episodesFor(podcastId: String): List<Episode> =
        episodes[podcastId].orEmpty().sortedByDescending { it.publishedAt }

    fun downloadFor(episodeId: String): Download? = downloads[episodeId]

    val queueEpisodes: List<Episode>
        get() = queueIds.mapNotNull(::episode)

    val favoriteEpisodes: List<Episode>
        get() = favoriteIds.mapNotNull(::episode).sortedByDescending { it.publishedAt }
}

@Singleton
class PodcastRepository @Inject constructor(
    private val client: OkHttpClient,
    private val prefs: LocalPrefs,
    private val downloader: EpisodeDownloader,
    private val itunes: ItunesSearch,
    private val playerHolder: PlayerHolder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistLock = Mutex()
    private val catalog = MutableStateFlow(CatalogCache())
    private val refreshing = MutableStateFlow<Set<String>>(emptySet())
    private val error = MutableStateFlow<String?>(null)
    private val ready = MutableStateFlow(false)

    val state: StateFlow<PodSnapshot> = combine(
        catalog,
        playerHolder.state,
        refreshing,
        error,
        ready,
    ) { cache, playback, refreshingIds, err, isReady ->
        PodSnapshot(
            podcasts = cache.podcasts.sortedBy { it.title.lowercase() },
            episodes = cache.episodes,
            downloads = cache.downloads,
            playback = playback,
            lastEpisodeId = cache.lastEpisodeId,
            refreshing = refreshingIds,
            ready = isReady,
            error = err,
            queueIds = cache.queueIds,
            playedIds = cache.playedIds,
            favoriteIds = cache.favoriteIds,
            playbackSpeed = cache.playbackSpeed,
            savedSpeeds = cache.savedSpeeds,
            skipBackSeconds = cache.skipBackSeconds,
            skipForwardSeconds = cache.skipForwardSeconds,
            grayscaleArtwork = cache.grayscaleArtwork,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PodSnapshot())

    init {
        playerHolder.onEnded = { episodeId -> onEpisodeEnded(episodeId) }
        scope.launch { start() }
        scope.launch {
            playerHolder.state.collect { playback ->
                catalog.update {
                    it.copy(
                        lastEpisodeId = playback.episodeId ?: it.lastEpisodeId,
                        lastPositionMs = playback.positionMs,
                    )
                }
            }
        }
        scope.launch {
            while (true) {
                delay(8_000)
                persist()
            }
        }
    }

    private suspend fun start() {
        val cached = withContext(Dispatchers.IO) { prefs.loadCatalog() }
        val downloads = cached.downloads.mapValues { (id, download) ->
            val file = downloader.fileFor(id)
            if (download.status == DownloadStatus.Done && file.exists()) {
                download.copy(path = file.absolutePath)
            } else if (download.status == DownloadStatus.Done && !file.exists()) {
                download.copy(status = DownloadStatus.None, path = null)
            } else if (download.status == DownloadStatus.Downloading || download.status == DownloadStatus.Queued) {
                download.copy(status = DownloadStatus.None, progress = 0f)
            } else {
                download
            }
        }
        catalog.value = cached.copy(downloads = downloads)
        playerHolder.setSpeed(cached.playbackSpeed)
        ready.value = true
        if (cached.podcasts.isNotEmpty()) {
            refreshAll()
        }
    }

    fun search(term: String): List<SearchHit> = itunes.search(term)

    suspend fun subscribe(feedUrl: String) {
        val url = feedUrl.trim()
        if (url.isEmpty()) return
        error.value = null
        val parsed = fetchFeed(url)
        catalog.update { cache ->
            val podcasts = cache.podcasts.filterNot { it.id == parsed.podcast.id } + parsed.podcast
            val episodes = cache.episodes + (parsed.podcast.id to parsed.episodes)
            cache.copy(podcasts = podcasts, episodes = episodes)
        }
        persist()
    }

    fun unsubscribe(podcastId: String) {
        scope.launch {
            val episodeIds = catalog.value.episodes[podcastId].orEmpty().map { it.id }
            episodeIds.forEach { downloader.delete(it) }
            catalog.update { cache ->
                cache.copy(
                    podcasts = cache.podcasts.filterNot { it.id == podcastId },
                    episodes = cache.episodes - podcastId,
                    downloads = cache.downloads.filterKeys { it !in episodeIds },
                    queueIds = cache.queueIds.filterNot { it in episodeIds },
                    playedIds = cache.playedIds - episodeIds.toSet(),
                    favoriteIds = cache.favoriteIds - episodeIds.toSet(),
                )
            }
            persist()
        }
    }

    fun refresh(podcastId: String) {
        val podcast = catalog.value.podcasts.firstOrNull { it.id == podcastId } ?: return
        scope.launch {
            refreshing.update { it + podcastId }
            try {
                subscribe(podcast.feedUrl)
            } catch (ex: Exception) {
                error.value = ex.message ?: "Could not refresh this feed"
            } finally {
                refreshing.update { it - podcastId }
            }
        }
    }

    fun refreshAll() {
        catalog.value.podcasts.forEach { refresh(it.id) }
    }

    fun download(episodeId: String) {
        val episode = findEpisode(episodeId) ?: return
        scope.launch {
            updateDownload(
                Download(episodeId = episodeId, status = DownloadStatus.Downloading, progress = 0f),
            )
            persist()
            try {
                val file = withContext(Dispatchers.IO) {
                    downloader.download(episode.id, episode.enclosureUrl) { bytes, total ->
                        val progress = if (total > 0) bytes.toFloat() / total.toFloat() else 0f
                        catalog.update { cache ->
                            cache.copy(
                                downloads = cache.downloads + (episodeId to Download(
                                    episodeId = episodeId,
                                    status = DownloadStatus.Downloading,
                                    progress = progress.coerceIn(0f, 1f),
                                    bytes = bytes,
                                    totalBytes = total,
                                )),
                            )
                        }
                    }
                }
                updateDownload(
                    Download(
                        episodeId = episodeId,
                        status = DownloadStatus.Done,
                        progress = 1f,
                        path = file.absolutePath,
                    ),
                )
                persist()
            } catch (ex: Exception) {
                updateDownload(
                    Download(
                        episodeId = episodeId,
                        status = DownloadStatus.Failed,
                        error = ex.message,
                    ),
                )
                persist()
            }
        }
    }

    fun deleteDownload(episodeId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { downloader.delete(episodeId) }
            catalog.update { cache -> cache.copy(downloads = cache.downloads - episodeId) }
            persist()
        }
    }

    fun play(episodeId: String) {
        val episode = findEpisode(episodeId) ?: return
        val podcast = catalog.value.podcasts.firstOrNull { it.id == episode.podcastId } ?: return
        val local = localFile(episodeId)
        val resume = if (catalog.value.lastEpisodeId == episodeId) catalog.value.lastPositionMs else 0L
        playerHolder.play(podcast, episode, local, resume)
        playerHolder.setSpeed(catalog.value.playbackSpeed)
        catalog.update { it.copy(lastEpisodeId = episodeId, lastPositionMs = resume) }
    }

    fun toggle() {
        val playback = playerHolder.state.value
        if (playback.episodeId == null) {
            val last = catalog.value.lastEpisodeId ?: return
            play(last)
        } else {
            playerHolder.toggle()
        }
    }

    fun seekTo(positionMs: Long) = playerHolder.seekTo(positionMs)

    fun skip(deltaMs: Long) = playerHolder.skip(deltaMs)

    fun skipBack() = skip(-catalog.value.skipBackSeconds * 1000L)

    fun skipForward() = skip(catalog.value.skipForwardSeconds * 1000L)

    fun setSkipBack(seconds: Int) {
        val value = normalizeSkipSeconds(seconds, catalog.value.skipBackSeconds)
        catalog.update { it.copy(skipBackSeconds = value) }
        persistSoon()
    }

    fun setSkipForward(seconds: Int) {
        val value = normalizeSkipSeconds(seconds, catalog.value.skipForwardSeconds)
        catalog.update { it.copy(skipForwardSeconds = value) }
        persistSoon()
    }

    fun setPlaybackSpeed(speed: Float) {
        val value = roundSpeed(speed)
        catalog.update { it.copy(playbackSpeed = value) }
        playerHolder.setSpeed(value)
        persistSoon()
    }

    fun saveSpeedPreset() {
        val speed = roundSpeed(catalog.value.playbackSpeed)
        catalog.update { cache ->
            cache.copy(savedSpeeds = (cache.savedSpeeds + speed).distinct().sorted())
        }
        persistSoon()
    }

    fun removeSpeedPreset(speed: Float) {
        val value = roundSpeed(speed)
        catalog.update { cache ->
            cache.copy(savedSpeeds = cache.savedSpeeds.filterNot { it == value })
        }
        persistSoon()
    }

    fun setGrayscaleArtwork(enabled: Boolean) {
        catalog.update { it.copy(grayscaleArtwork = enabled) }
        persistSoon()
    }

    fun enqueue(episodeId: String, atFront: Boolean = false) {
        if (findEpisode(episodeId) == null) return
        catalog.update { cache ->
            val without = cache.queueIds.filterNot { it == episodeId }
            val ids = if (atFront) listOf(episodeId) + without else without + episodeId
            cache.copy(queueIds = ids)
        }
        persistSoon()
    }

    fun removeFromQueue(episodeId: String) {
        catalog.update { cache -> cache.copy(queueIds = cache.queueIds.filterNot { it == episodeId }) }
        persistSoon()
    }

    fun moveQueue(from: Int, to: Int) {
        catalog.update { cache -> cache.copy(queueIds = cache.queueIds.moved(from, to)) }
        persistSoon()
    }

    fun moveQueueToTop(episodeId: String) {
        catalog.update { cache ->
            if (episodeId !in cache.queueIds) return@update cache
            cache.copy(queueIds = listOf(episodeId) + cache.queueIds.filterNot { it == episodeId })
        }
        persistSoon()
    }

    fun moveQueueToBottom(episodeId: String) {
        catalog.update { cache ->
            if (episodeId !in cache.queueIds) return@update cache
            cache.copy(queueIds = cache.queueIds.filterNot { it == episodeId } + episodeId)
        }
        persistSoon()
    }

    fun toggleFavorite(episodeId: String) {
        if (findEpisode(episodeId) == null) return
        catalog.update { cache ->
            val next = if (episodeId in cache.favoriteIds) cache.favoriteIds - episodeId else cache.favoriteIds + episodeId
            cache.copy(favoriteIds = next)
        }
        persistSoon()
    }

    fun markPlayed(episodeId: String, played: Boolean = true) {
        catalog.update { cache ->
            cache.copy(playedIds = if (played) cache.playedIds + episodeId else cache.playedIds - episodeId)
        }
        persistSoon()
    }

    fun playAdjacent(next: Boolean) {
        val current = state.value.currentEpisode ?: return
        val queue = catalog.value.queueIds
        val queueIndex = queue.indexOf(current.id)
        if (queueIndex >= 0) {
            val targetId = if (next) queue.getOrNull(queueIndex + 1) else queue.getOrNull(queueIndex - 1)
            if (targetId != null) {
                play(targetId)
                return
            }
            if (next) return
        } else if (next && queue.isNotEmpty()) {
            play(queue.first())
            return
        }
        val list = state.value.episodesFor(current.podcastId)
        val index = list.indexOfFirst { it.id == current.id }
        if (index < 0) return
        val target = if (next) list.getOrNull(index + 1) else list.getOrNull(index - 1)
        if (target != null) play(target.id)
    }

    private fun onEpisodeEnded(episodeId: String) {
        markPlayed(episodeId, played = true)
        val queue = catalog.value.queueIds
        val played = catalog.value.playedIds
        val index = queue.indexOf(episodeId)
        val nextId = if (index >= 0) {
            queue.drop(index + 1).firstOrNull { it !in played }
        } else {
            queue.firstOrNull { it !in played }
        }
        if (nextId != null) play(nextId)
    }

    private fun persistSoon() {
        scope.launch { persist() }
    }

    private fun localFile(episodeId: String): File? {
        val download = catalog.value.downloads[episodeId]
        val file = downloader.fileFor(episodeId)
        return file.takeIf { download?.status == DownloadStatus.Done && it.exists() }
    }

    private fun findEpisode(episodeId: String): Episode? =
        catalog.value.episodes.values.flatten().firstOrNull { it.id == episodeId }

    private fun updateDownload(download: Download) {
        catalog.update { cache -> cache.copy(downloads = cache.downloads + (download.episodeId to download)) }
    }

    private suspend fun fetchFeed(feedUrl: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feedUrl)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Feed request failed (${response.code})")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("Empty feed")
            RssParser.parse(body, feedUrl)
        }
    }

    private suspend fun persist() {
        persistLock.withLock {
            val cache = catalog.value
            withContext(Dispatchers.IO) { prefs.saveCatalog(cache) }
        }
    }
}
