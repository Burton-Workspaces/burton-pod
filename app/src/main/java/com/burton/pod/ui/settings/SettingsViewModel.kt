package com.burton.pod.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.pod.data.repository.DiscoveryFeedsStore
import com.burton.pod.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val discovery: DiscoveryFeedsStore,
) : ViewModel() {
    val snapshot = repository.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        repository.state.value,
    )
    val feeds = discovery.feeds
    val selectedId = discovery.selectedId
    val selected = discovery.selected
    private val _feedError = MutableStateFlow<String?>(null)
    val feedError = _feedError.asStateFlow()

    fun selectFeed(id: String) {
        _feedError.value = null
        discovery.select(id)
    }

    fun removeFeed(id: String) {
        _feedError.value = null
        discovery.remove(id)
    }

    fun addFeed(name: String, url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _feedError.value = null
            try {
                discovery.add(name, url)
                onSuccess()
            } catch (ex: Exception) {
                _feedError.value = ex.message ?: "Could not add this feed"
            }
        }
    }

    fun setGrayscale(enabled: Boolean) = repository.setGrayscaleArtwork(enabled)
}
