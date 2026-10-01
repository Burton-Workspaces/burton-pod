package com.burton.pod.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.pod.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.state.value)

    fun play(episodeId: String) = repository.play(episodeId)

    fun move(from: Int, to: Int) = repository.moveQueue(from, to)

    fun moveToTop(episodeId: String) = repository.moveQueueToTop(episodeId)

    fun moveToBottom(episodeId: String) = repository.moveQueueToBottom(episodeId)

    fun markPlayed(episodeId: String, played: Boolean) = repository.markPlayed(episodeId, played)

    fun remove(episodeId: String) = repository.removeFromQueue(episodeId)

    fun deleteDownload(episodeId: String) = repository.deleteDownload(episodeId)

    fun toggleFavorite(episodeId: String) = repository.toggleFavorite(episodeId)
}
