package com.burton.pod.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.pod.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.state.value)

    fun refreshAll() = repository.refreshAll()

    fun toggle() = repository.toggle()

    fun skipForward() = repository.skipForward()

    fun play(episodeId: String) = repository.play(episodeId)

    fun toggleFavorite(episodeId: String) = repository.toggleFavorite(episodeId)
}
