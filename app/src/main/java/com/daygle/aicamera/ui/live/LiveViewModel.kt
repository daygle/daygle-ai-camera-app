package com.daygle.aicamera.ui.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.StatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveUiState(
    val cameraId: String,
    val cameraName: String = "",
    val frameUrl: String? = null,
    val playing: Boolean = true,
    val status: StatusResponse? = null,
)

/**
 * Drives the live view by polling `/api/live/snapshot`. The server exposes
 * per-frame JPEG snapshots (not an MJPEG/WebRTC stream), so a steady refresh at
 * the configured refresh interval provides a near-live feed while staying easy on the
 * server. Playback pauses automatically when the screen leaves the foreground.
 */
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val repository: CameraRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cameraId: String = savedStateHandle.get<String>(ARG_CAMERA_ID).orEmpty()

    private val _state = MutableStateFlow(LiveUiState(cameraId = cameraId))
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private var frameFetchJob: Job? = null

    init {
        _state.update { it.copy(cameraName = cameraId) }
        loadStatus()
        fetchNextFrame()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            repository.status(cameraId).getOrNull()?.let { status ->
                _state.update {
                    it.copy(
                        status = status,
                        cameraName = status.cameraName?.takeIf(String::isNotBlank) ?: it.cameraName,
                    )
                }
            }
        }
    }

    fun fetchNextFrame() {
        if (!_state.value.playing || frameFetchJob?.isActive == true) return
        frameFetchJob = viewModelScope.launch {
            val thisJob = currentCoroutineContext()[Job]
            try {
                val intervalMs = repository.appPrefs().currentRefreshIntervalMs()
                delay(intervalMs)
                if (_state.value.playing) {
                    val url = repository.snapshotUrl(cameraId, System.currentTimeMillis())
                    // Release the guard before publishing the frame so the
                    // image loader can immediately schedule the next refresh.
                    frameFetchJob = null
                    _state.update { it.copy(frameUrl = url) }
                }
            } finally {
                if (frameFetchJob === thisJob) frameFetchJob = null
            }
        }
    }

    fun togglePlayback() {
        val shouldResume = !_state.value.playing
        _state.update { it.copy(playing = shouldResume) }
        if (shouldResume) fetchNextFrame()
    }

    fun pause() {
        _state.update { it.copy(playing = false) }
        frameFetchJob?.cancel()
        frameFetchJob = null
    }

    fun resume() {
        val shouldResume = !_state.value.playing
        _state.update { it.copy(playing = true) }
        if (shouldResume) fetchNextFrame()
    }

    companion object {
        const val ARG_CAMERA_ID = "cameraId"
    }
}
