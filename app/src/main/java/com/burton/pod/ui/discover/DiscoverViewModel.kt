package com.burton.pod.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.pod.data.repository.DiscoveryFeedsStore
import com.burton.pod.data.repository.PodcastRepository
import com.burton.pod.data.search.DiscoveryCatalog
import com.burton.pod.domain.DiscoveryFeed
import com.burton.pod.domain.SearchHit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiscoverUi(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val loading: Boolean = true,
    val searched: Boolean = false,
    val error: String? = null,
    val feedUrl: String = "",
    val adding: Boolean = false,
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val discoveryFeeds: DiscoveryFeedsStore,
    private val catalog: DiscoveryCatalog,
) : ViewModel() {
    val snapshot = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.state.value)
    private val _ui = MutableStateFlow(DiscoverUi())
    val ui = _ui.asStateFlow()
    private var searchJob: Job? = null
    private var feedHits: List<SearchHit> = emptyList()

    init {
        viewModelScope.launch {
            discoveryFeeds.ready.first { it }
            discoveryFeeds.selected.collectLatest { loadFeed(it) }
        }
    }

    fun onQueryChange(value: String) {
        _ui.update { it.copy(query = value) }
        searchJob?.cancel()
        val term = value.trim()
        if (term.length < 2) {
            _ui.update { it.copy(hits = feedHits, searched = false, loading = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val hits = withContext(Dispatchers.IO) { repository.search(term) }
                _ui.update { it.copy(hits = hits, loading = false, searched = true) }
            } catch (ex: Exception) {
                _ui.update { it.copy(loading = false, searched = true, error = ex.message, hits = emptyList()) }
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _ui.update { it.copy(query = "", hits = feedHits, searched = false, loading = false, error = null) }
    }

    fun onFeedUrlChange(value: String) {
        _ui.update { it.copy(feedUrl = value) }
    }

    fun subscribeHit(hit: SearchHit) {
        viewModelScope.launch { addResolved(hit) }
    }

    fun subscribeFeed() {
        viewModelScope.launch { add(_ui.value.feedUrl) }
    }

    private suspend fun loadFeed(feed: DiscoveryFeed) {
        searchJob?.cancel()
        _ui.update { it.copy(loading = true, error = null, query = "", searched = false) }
        try {
            val hits = withContext(Dispatchers.IO) { catalog.load(feed.url) }
            feedHits = hits
            _ui.update { it.copy(hits = hits, loading = false, error = null) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            feedHits = emptyList()
            _ui.update {
                it.copy(
                    hits = emptyList(),
                    loading = false,
                    error = ex.message ?: "Could not load this discovery feed",
                )
            }
        }
    }

    private suspend fun addResolved(hit: SearchHit) {
        _ui.update { it.copy(adding = true, error = null) }
        try {
            val url = withContext(Dispatchers.IO) { catalog.resolveFeedUrl(hit) }
            repository.subscribe(url)
            _ui.update { it.copy(adding = false) }
        } catch (ex: Exception) {
            _ui.update { it.copy(adding = false, error = ex.message ?: "Could not subscribe") }
        }
    }

    private suspend fun add(url: String) {
        _ui.update { it.copy(adding = true, error = null) }
        try {
            repository.subscribe(url)
            _ui.update { it.copy(adding = false, feedUrl = "") }
        } catch (ex: Exception) {
            _ui.update { it.copy(adding = false, error = ex.message ?: "Could not subscribe") }
        }
    }
}
