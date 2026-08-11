package com.daygle.aicamera.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraCard(
    val camera: Camera,
    val online: Boolean,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Error(val message: String) : DashboardUiState
    data class Ready(
        val cameras: List<CameraCard>,
        val snapshotUrls: Map<String, String> = emptyMap(),
        val refreshing: Boolean = false,
    ) : DashboardUiState
}

/**
 * Drives the camera grid by polling `/api/live/snapshot` for every camera at
 * the configured refresh interval, so each tile shows a near-live feed. The
 * same per-frame JPEG approach is used by the full-screen Live view.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(private val repository: CameraRepository) : ViewModel() {

    private val _state = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        load()
        startPolling()
    }

    fun load() {
        val existing = _state.value
        if (existing is DashboardUiState.Ready) {
            _state.value = existing.copy(refreshing = true)
        } else {
            _state.value = DashboardUiState.Loading
        }
        viewModelScope.launch {
            val camerasResult = repository.cameras()
            val cameras = camerasResult.getOrElse {
                _state.value = DashboardUiState.Error(it.friendlyMessage())
                return@launch
            }
            val health = repository.cameraHealth().getOrNull()
            val cards = cameras.map { camera ->
                CameraCard(
                    camera = camera,
                    online = health?.cameras?.get(camera.id)?.online ?: true,
                )
            }
            _state.value = DashboardUiState.Ready(
                cameras = cards,
                snapshotUrls = freshSnapshotUrls(cards),
            )
        }
    }

    /** Pause polling when the screen is backgrounded or covered. */
    fun pause() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Resume polling with an immediate fresh frame. */
    fun resume() {
        startPolling()
        _state.update { current ->
            if (current is DashboardUiState.Ready) {
                current.copy(snapshotUrls = freshSnapshotUrls(current.cameras))
            } else current
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val intervalMs = repository.appPrefs().currentRefreshIntervalMs().coerceAtLeast(500)
                delay(intervalMs)
                _state.update { current ->
                    if (current is DashboardUiState.Ready) {
                        current.copy(snapshotUrls = freshSnapshotUrls(current.cameras))
                    } else current
                }
            }
        }
    }

    /** Cache-busted snapshot URL for every camera card. */
    private fun freshSnapshotUrls(cards: List<CameraCard>): Map<String, String> {
        val now = System.currentTimeMillis()
        return cards.associate { card ->
            card.camera.id to (repository.snapshotUrl(card.camera.id, now) ?: "")
        }.filterValues { it.isNotEmpty() }
    }
}

fun Throwable.friendlyMessage(): String = when (this) {
    is java.net.UnknownHostException -> "Can't find the server. Check the address and your internet connection."
    is java.net.ConnectException -> "Can't reach the server. It may be offline, or the address may be wrong."
    is java.net.SocketTimeoutException -> "The server took too long to respond. Check your connection and try again."
    is com.daygle.aicamera.data.CloudflareAccessRequiredException ->
        "Access was rejected. Sign in again to reconnect to your server."
    is retrofit2.HttpException -> httpStatusMessage(code())
    is java.net.HttpRetryException -> httpStatusMessage(responseCode())
    is java.io.IOException -> "Network problem. Check your internet connection and try again."
    else -> message ?: "Something went wrong. Please try again."
}

/** Maps an HTTP status code to a user-facing explanation. */
private fun httpStatusMessage(code: Int): String = when (code) {
    401, 403 -> "Access denied. You may need to sign in again."
    404 -> "The server couldn't be found at this address. It may be misconfigured."
    408 -> "The request timed out. Please try again."
    429 -> "Too many requests. Please wait a moment and try again."
    500 -> "The server ran into a problem. Please try again shortly."
    502, 503, 504 -> "The server is temporarily unavailable. Please try again shortly."
    // Cloudflare origin errors (520–530): the tunnel/server can't be reached.
    in 520..530 -> "Can't reach your camera server right now. It may be offline or still starting up."
    else -> "The server returned an error (HTTP $code). Please try again."
}
