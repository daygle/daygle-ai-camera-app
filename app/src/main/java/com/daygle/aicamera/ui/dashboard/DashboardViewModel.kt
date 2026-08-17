package com.daygle.aicamera.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.ui.friendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
        val snapshotUrls: Map<String, String> = emptyMap(),
        val refreshing: Boolean = false,
    ) : DashboardUiState
}

/**
 * Drives the camera grid by polling `/api/live/snapshot` for every camera at
 * the configured refresh interval, so each tile shows a near-live feed.
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

    fun pause() {
        pollJob?.cancel()
        pollJob = null
    }

    fun resume() {
        if (pollJob == null) {
            startPolling()
        }
        _state.update { current ->
            if (current is DashboardUiState.Ready) {
                current.copy(snapshotUrls = freshSnapshotUrls(current.cameras))
            } else current
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repository.appPrefs().refreshIntervalMs.collectLatest { interval ->
                val safeInterval = interval.coerceAtLeast(500)
                while (true) {
                    delay(safeInterval)
                    _state.update { current ->
                        if (current is DashboardUiState.Ready) {
                            current.copy(snapshotUrls = freshSnapshotUrls(current.cameras))
                        } else current
                    }
                }
            }
        }
    }

    private fun freshSnapshotUrls(cards: List<CameraCard>): Map<String, String> {
        val now = System.currentTimeMillis()
        return cards.associate { card ->
            card.camera.id to (repository.snapshotUrl(card.camera.id, now) ?: "")
        }.filterValues { it.isNotEmpty() }
    }
}
