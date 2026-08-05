package com.daygle.aicamera.ui.snapshots

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import com.daygle.aicamera.util.FileDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnapshotsFilter(
    val query: String = "",
)

data class SnapshotsReady(
    val snapshots: List<Event>,
    val filtered: List<Event>,
    val filter: SnapshotsFilter = SnapshotsFilter(),
    val refreshing: Boolean = false,
)

sealed interface SnapshotsUiState {
    data object Loading : SnapshotsUiState
    data class Error(val message: String) : SnapshotsUiState
    data class Ready(val data: SnapshotsReady) : SnapshotsUiState
}

@HiltViewModel
class SnapshotsViewModel @Inject constructor(
    private val repository: CameraRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<SnapshotsUiState>(SnapshotsUiState.Loading)
    val state: StateFlow<SnapshotsUiState> = _state.asStateFlow()

    private val downloader = FileDownloader(context, repository.httpClient())

    private var allSnapshots: List<Event> = emptyList()

    init {
        load()
    }

    fun load() {
        val current = _state.value
        _state.value = if (current is SnapshotsUiState.Ready) {
            SnapshotsUiState.Ready(current.data.copy(refreshing = true))
        } else {
            SnapshotsUiState.Loading
        }

        viewModelScope.launch {
            val eventsResult = repository.events()
            if (eventsResult.isSuccess) {
                allSnapshots = eventsResult.getOrThrow()
                    .filter { it.hasSnapshot || !it.snapshotPath.isNullOrBlank() }
                    .sortedByDescending { it.id }
                val filter = (_state.value as? SnapshotsUiState.Ready)?.data?.filter ?: SnapshotsFilter()
                _state.value = SnapshotsUiState.Ready(
                    SnapshotsReady(
                        snapshots = allSnapshots,
                        filtered = applyFilter(filter),
                        filter = filter,
                    ),
                )
            } else {
                val error = eventsResult.exceptionOrNull()
                _state.value = SnapshotsUiState.Error(error?.friendlyMessage() ?: "Unknown error")
            }
        }
    }

    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)

    fun setQuery(query: String) {
        _state.update { current ->
            if (current is SnapshotsUiState.Ready) {
                val filter = current.data.filter.copy(query = query)
                SnapshotsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilter(filter)))
            } else {
                current
            }
        }
    }

    fun download(url: String, fileName: String) {
        viewModelScope.launch {
            downloader.downloadFile(url, fileName, "image/jpeg")
        }
    }

    private fun applyFilter(filter: SnapshotsFilter): List<Event> {
        if (filter.query.isBlank()) return allSnapshots
        val query = filter.query.trim().lowercase()
        return allSnapshots.filter { event ->
            event.id.toString().contains(query) ||
                event.source?.lowercase()?.contains(query) == true ||
                event.topLabel?.lowercase()?.contains(query) == true ||
                event.detections.any { it.label.lowercase().contains(query) }
        }
    }
}
