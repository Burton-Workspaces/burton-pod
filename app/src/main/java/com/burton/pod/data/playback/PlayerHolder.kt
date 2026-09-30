package com.burton.pod.data.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.burton.pod.domain.Episode
import com.burton.pod.domain.PlaybackState
import com.burton.pod.domain.Podcast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var ticker: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                if (isPlaying) startTicker() else ticker?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                publish()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publish()
            }
        })
    }

    fun play(podcast: Podcast, episode: Episode, localFile: File?, startPositionMs: Long = 0L) {
        val uri = if (localFile != null && localFile.exists()) {
            Uri.fromFile(localFile)
        } else {
            Uri.parse(episode.enclosureUrl)
        }
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(episode.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast.title)
                    .setArtworkUri(episode.artworkUrl?.let(Uri::parse) ?: podcast.artworkUrl?.let(Uri::parse))
                    .build(),
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.play()
        startService()
        publish()
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
        startService()
        publish()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    fun skip(deltaMs: Long) {
        seekTo(player.currentPosition + deltaMs)
    }

    fun stop() {
        player.stop()
        publish()
    }

    private fun startService() {
        val intent = Intent(context, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                publish()
                delay(400)
            }
        }
    }

    private fun publish() {
        _state.value = PlaybackState(
            episodeId = player.currentMediaItem?.mediaId,
            playing = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
        )
    }
}
