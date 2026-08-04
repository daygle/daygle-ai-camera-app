package com.daygle.aicamera.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.daygle.aicamera.data.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CameraRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordingId: Int = savedStateHandle.get<Int>("recordingId") ?: 0

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @OptIn(UnstableApi::class)
    val player: ExoPlayer = createPlayer()

    private val mediaSession: MediaSession = MediaSession.Builder(context, player).build()

    init {
        preparePlayer()
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(): ExoPlayer {
        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(repository.httpClient())
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(playbackError: androidx.media3.common.PlaybackException) {
                        val message = when (playbackError.errorCode) {
                            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> 
                                "Video decoding failed. The resolution might be too high for this device."
                            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                "Network connection failed. Check your server address."
                            else -> "Playback error: ${playbackError.localizedMessage}"
                        }
                        _error.update { message }
                    }
                })
            }
    }

    private fun preparePlayer() {
        val streamUrl = repository.recordingStreamUrl(recordingId)
        if (streamUrl != null) {
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.playWhenReady = true
        } else {
            _error.update { "This recording can't be played right now." }
        }
    }

    fun retry() {
        _error.update { null }
        player.stop()
        player.clearMediaItems()
        preparePlayer()
    }

    override fun onCleared() {
        super.onCleared()
        mediaSession.release()
        player.release()
    }
}
