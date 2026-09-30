package com.burton.pod.ui.show

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.pod.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ShowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PodcastRepository,
) : ViewModel() {
    val podcastId: String = savedStateHandle.get<String>("podcastId").orEmpty()
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.state.value)

    fun refresh() = repository.refresh(podcastId)

    fun unsubscribe() = repository.unsubscribe(podcastId)

    fun play(episodeId: String) = repository.play(episodeId)

    fun download(episodeId: String) = repository.download(episodeId)

    fun deleteDownload(episodeId: String) = repository.deleteDownload(episodeId)
}
