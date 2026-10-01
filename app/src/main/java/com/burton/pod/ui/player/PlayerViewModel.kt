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

    fun skipBack() = repository.skipBack()

    fun skipForward() = repository.skipForward()

    fun previous() = repository.playAdjacent(next = false)

    fun next() = repository.playAdjacent(next = true)

    fun setSkipBack(seconds: Int) = repository.setSkipBack(seconds)

    fun setSkipForward(seconds: Int) = repository.setSkipForward(seconds)

    fun setSpeed(speed: Float) = repository.setPlaybackSpeed(speed)

    fun saveSpeedPreset() = repository.saveSpeedPreset()

    fun removeSpeedPreset(speed: Float) = repository.removeSpeedPreset(speed)

    fun toggleFavorite() {
        val id = state.value.currentEpisode?.id ?: return
        repository.toggleFavorite(id)
    }
}
