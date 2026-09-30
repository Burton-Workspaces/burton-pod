package com.burton.pod.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.pod.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.state.value)

    fun toggle() = repository.toggle()

    fun seekTo(positionMs: Long) = repository.seekTo(positionMs)

    fun skipBack() = repository.skip(-10_000)

    fun skipForward() = repository.skip(30_000)

    fun previous() = repository.playAdjacent(next = false)

    fun next() = repository.playAdjacent(next = true)
}
