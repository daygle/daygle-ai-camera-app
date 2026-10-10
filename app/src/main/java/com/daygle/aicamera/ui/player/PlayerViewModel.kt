package com.daygle.aicamera.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.util.FileDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CameraRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordingId: Int = savedStateHandle.get<Int>("recordingId") ?: 0

    /** Clip metadata (detections, AI description and tags, faces); null until loaded or if unavailable. */
    private val _details = MutableStateFlow<Recording?>(null)
    val details: StateFlow<Recording?> = _details.asStateFlow()

    /** Camera id -> display name lookup, populated once cameras are loaded. */
    private val _cameraNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val cameraNames: StateFlow<Map<String, String>> = _cameraNames.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @OptIn(UnstableApi::class)
    val player: ExoPlayer = createPlayer()

    private val mediaSession: MediaSession = MediaSession.Builder(context, player).build()

    private val downloader = FileDownloader(context, repository.httpClient())

    init {
        preparePlayer()
        loadDetails()
        loadCameraNames()
    }

    /** Best-effort camera name lookup; playback works without it. */
    private fun loadCameraNames() {
        viewModelScope.launch {
            repository.cameras().onSuccess { cameras ->
                _cameraNames.value = cameras.associate { it.id to it.displayName }
            }
        }
    }

    private fun loadDetails() {
        if (recordingId <= 0) return
        viewModelScope.launch {
            // Best effort: playback works without the details panel.
            repository.recording(recordingId).onSuccess { recording -> _details.value = recording }
        }
    }

    /** Save the current recording's MP4 to the device's downloads. */
    fun download() {
        val url = repository.recordingDownloadUrl(recordingId) ?: return
        viewModelScope.launch {
            downloader.downloadFile(url, "recording-$recordingId.mp4", "video/mp4")
        }
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(): ExoPlayer {
        // The server converts an H.265 clip to H.264 on its first play (event
        // clips are usually converted in the background beforehand, continuous
        // chunks are not), and answers only once the copy is written. Allow
        // for that instead of the API client's 30 s read timeout.
        val streamClient = repository.httpClient().newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamClient)
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
                    override fun onPlayerError(playbackError: PlaybackException) {
                        val message = playbackErrorMessage(playbackError)
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

    @OptIn(UnstableApi::class)
    private fun playbackErrorMessage(error: PlaybackException): String {
        when ((error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode) {
            415 -> return "The server could not convert this recording into a playable video."
            404 -> return "This recording's video file is no longer on the server."
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "Video decoding failed. The resolution might be too high for this device."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network connection failed. Check your server address."
            else -> "Playback error: ${error.localizedMessage}"
        }
    }

    private companion object {
        /** Long enough for the server to finish a first-play H.265 conversion. */
        const val STREAM_READ_TIMEOUT_SECONDS = 180L
    }

    override fun onCleared() {
        mediaSession.release()
        player.release()
    }
}
