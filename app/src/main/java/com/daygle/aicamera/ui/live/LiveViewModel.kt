package com.daygle.aicamera.ui.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.StatusResponse
import com.daygle.aicamera.ui.repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
 * [REFRESH_INTERVAL_MS] provides a near-live feed while staying easy on the
 * server. Playback pauses automatically when the screen leaves the foreground.
 */
class LiveViewModel(
    private val repository: CameraRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cameraId: String = savedStateHandle.get<String>(ARG_CAMERA_ID).orEmpty()

    private val _state = MutableStateFlow(LiveUiState(cameraId = cameraId))
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    @Volatile
    private var isFetching = false

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
        if (!_state.value.playing || isFetching) return
        isFetching = true
        viewModelScope.launch {
            try {
                val intervalMs = repository.appPrefs().currentRefreshIntervalMs()
                delay(intervalMs)
                val url = repository.snapshotUrl(cameraId, System.currentTimeMillis())
                _state.update { it.copy(frameUrl = url) }
            } finally {
                isFetching = false
            }
        }
    }

    fun togglePlayback() {
        _state.update { 
            val next = !it.playing
            if (next) fetchNextFrame()
            it.copy(playing = next) 
        }
    }

    fun pause() = _state.update { it.copy(playing = false) }
    
    fun resume() {
        _state.update { 
            if (!it.playing) fetchNextFrame()
            it.copy(playing = true) 
        }
    }

    companion object {
        const val ARG_CAMERA_ID = "cameraId"
        private const val REFRESH_INTERVAL_MS = 700L

        val Factory = viewModelFactory {
            initializer { LiveViewModel(repository(), createSavedStateHandle()) }
        }
    }
}
