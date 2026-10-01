package com.burton.pod.data.repository

import com.burton.pod.data.parse.TinyJson
import com.burton.pod.data.parse.TinyJson.objList
import com.burton.pod.data.parse.TinyJson.str
import com.burton.pod.domain.DefaultDiscoveryFeeds
import com.burton.pod.domain.DiscoveryFeed
import com.burton.pod.domain.Ids
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveryPrefs(
    val selectedId: String = DefaultDiscoveryFeeds.SPOTIFY_TOP_ID,
    val custom: List<DiscoveryFeed> = emptyList(),
) {
    fun encode(): String = TinyJson.stringify(
        mapOf(
            "selectedId" to selectedId,
            "custom" to custom.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "url" to it.url,
                )
            },
        ),
    )

    companion object {
        fun decode(json: String): DiscoveryPrefs {
            if (json.isBlank()) return DiscoveryPrefs()
            val root = TinyJson.parseObject(json)
            val custom = root.objList("custom").mapNotNull { row ->
                val url = row.str("url").trim()
                val name = row.str("name").trim()
                if (url.isBlank() || name.isBlank()) return@mapNotNull null
                DiscoveryFeed(
                    id = row.str("id").ifBlank { Ids.podcast(url) },
                    name = name,
                    url = url,
                    builtIn = false,
                )
            }
            return DiscoveryPrefs(
                selectedId = root.str("selectedId").ifBlank { DefaultDiscoveryFeeds.SPOTIFY_TOP_ID },
                custom = custom,
            )
        }

        fun merge(saved: DiscoveryPrefs): Pair<List<DiscoveryFeed>, String> {
            val builtInUrls = DefaultDiscoveryFeeds.all.map { normalizeUrl(it.url) }.toSet()
            val custom = saved.custom.filter { normalizeUrl(it.url) !in builtInUrls }
            val feeds = DefaultDiscoveryFeeds.all + custom
            val selectedId = feeds.firstOrNull { it.id == saved.selectedId }?.id
                ?: DefaultDiscoveryFeeds.SPOTIFY_TOP_ID
            return feeds to selectedId
        }

        fun normalizeUrl(url: String): String = url.trim().trimEnd('/')
    }
}

@Singleton
class DiscoveryFeedsStore @Inject constructor(
    private val prefs: LocalPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _feeds = MutableStateFlow(DefaultDiscoveryFeeds.all)
    private val _selectedId = MutableStateFlow(DefaultDiscoveryFeeds.SPOTIFY_TOP_ID)
    private val _ready = MutableStateFlow(false)

    val feeds = _feeds.asStateFlow()
    val selectedId = _selectedId.asStateFlow()
    val ready = _ready.asStateFlow()
    val selected = combine(feeds, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.first()
    }.stateIn(scope, SharingStarted.Eagerly, DefaultDiscoveryFeeds.all.first())

    init {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { prefs.loadDiscovery() }.getOrDefault(DiscoveryPrefs())
            }
            val (merged, selectedId) = DiscoveryPrefs.merge(saved)
            _feeds.value = merged
            _selectedId.value = selectedId
            _ready.value = true
        }
    }

    fun select(id: String) {
        if (_feeds.value.none { it.id == id }) return
        _selectedId.value = id
        persist()
    }

    suspend fun add(name: String, url: String) {
        val title = name.trim()
        val address = url.trim()
        if (title.isBlank()) error("Give this feed a name")
        if (address.isBlank()) error("Paste a discovery feed URL")
        val parsed = runCatching { address.toHttpUrl() }.getOrNull()
            ?: error("That URL is not valid")
        if (parsed.scheme != "http" && parsed.scheme != "https") {
            error("Enter a URL starting with https://")
        }
        val normalized = DiscoveryPrefs.normalizeUrl(parsed.toString())
        if (_feeds.value.any { DiscoveryPrefs.normalizeUrl(it.url) == normalized }) {
            error("That feed is already in the list")
        }
        val feed = DiscoveryFeed(
            id = Ids.podcast(normalized),
            name = title,
            url = parsed.toString(),
            builtIn = false,
        )
        _feeds.value = _feeds.value + feed
        _selectedId.value = feed.id
        persist()
    }

    fun remove(id: String) {
        val current = _feeds.value.firstOrNull { it.id == id } ?: return
        if (current.builtIn) return
        val remaining = _feeds.value.filterNot { it.id == id }
        _feeds.value = remaining
        if (_selectedId.value == id) {
            _selectedId.value = DefaultDiscoveryFeeds.SPOTIFY_TOP_ID
        }
        persist()
    }

    private fun persist() {
        scope.launch {
            val snapshot = DiscoveryPrefs(
                selectedId = _selectedId.value,
                custom = _feeds.value.filterNot { it.builtIn },
            )
            withContext(Dispatchers.IO) { prefs.saveDiscovery(snapshot) }
        }
    }
}
