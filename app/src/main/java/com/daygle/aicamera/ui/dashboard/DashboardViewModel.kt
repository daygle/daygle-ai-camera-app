package com.daygle.aicamera.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.CameraHealthSummary
import com.daygle.aicamera.data.model.StatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        val refreshing: Boolean = false,
    ) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(private val repository: CameraRepository) : ViewModel() {

    private val _state = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** A one-shot snapshot URL for a camera tile (cache-busted so it isn't reused stale). */
    fun snapshotUrl(cameraId: String): String? =
        repository.snapshotUrl(cameraId, System.currentTimeMillis() / 5000)

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
            )
        }
    }
}

fun Throwable.friendlyMessage(): String = when (this) {
    is java.net.UnknownHostException -> "Server not found. Check the address."
    is java.net.ConnectException -> "Could not connect to the server."
    is java.net.SocketTimeoutException -> "The server took too long to respond."
    is java.net.HttpRetryException -> "Server returned ${this.responseCode()}. Try reconnecting."
    else -> (message ?: "Something went wrong.") + " (${this::class.java.simpleName})"
}
