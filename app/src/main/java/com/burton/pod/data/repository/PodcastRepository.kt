package com.burton.pod.data.repository

import com.burton.pod.data.download.EpisodeDownloader
import com.burton.pod.data.parse.RssParser
import com.burton.pod.data.playback.PlayerHolder
import com.burton.pod.data.search.ItunesSearch
import com.burton.pod.domain.Download
import com.burton.pod.domain.DownloadStatus
import com.burton.pod.domain.Episode
import com.burton.pod.domain.PlaybackState
import com.burton.pod.domain.Podcast
import com.burton.pod.domain.SearchHit
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
) {
    val currentEpisode: Episode?
        get() {
            val id = playback.episodeId ?: lastEpisodeId
            return id?.let { wanted -> episodes.values.flatten().firstOrNull { it.id == wanted } }
        }

    fun podcast(id: String): Podcast? = podcasts.firstOrNull { it.id == id }

    fun episodesFor(podcastId: String): List<Episode> =
        episodes[podcastId].orEmpty().sortedByDescending { it.publishedAt }

    fun downloadFor(episodeId: String): Download? = downloads[episodeId]
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
        )
    }.stateIn(scope, SharingStarted.Eagerly, PodSnapshot())

    init {
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

    fun playAdjacent(next: Boolean) {
        val current = state.value.currentEpisode ?: return
        val list = state.value.episodesFor(current.podcastId)
        val index = list.indexOfFirst { it.id == current.id }
        if (index < 0) return
        val target = if (next) list.getOrNull(index + 1) else list.getOrNull(index - 1)
        if (target != null) play(target.id)
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
