package com.daygle.aicamera.ui.snapshots

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import com.daygle.aicamera.ui.isMotionLabel
import com.daygle.aicamera.ui.isSoundLabel
import com.daygle.aicamera.util.FileDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

enum class SnapshotsSortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
}

data class SnapshotsFilter(
    val query: String = "",
    val dateStart: LocalDate? = LocalDate.now(),
    val dateEnd: LocalDate? = LocalDate.now(),
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val sortOrder: SnapshotsSortOrder = SnapshotsSortOrder.NEWEST,
) {
    fun activeCount(): Int {
        var count = 0
        if (query.isNotBlank()) count++
        count += selectedModes.size
        count += selectedCameras.size
        count += selectedTriggerTypes.size
        count += selectedLabels.size
        return count
    }
}

data class SnapshotsReady(
    val snapshots: List<Event>,
    val filtered: List<Event>,
    val cameras: List<Camera> = emptyList(),
    val filter: SnapshotsFilter = SnapshotsFilter(),
    val refreshing: Boolean = false,
) {
    val availableModes: List<String> = listOf("Object", "Sound", "Motion")

    val availableSources: List<String> =
        snapshots.mapNotNull { it.source }.filter { it != "sound" && it != "rtsp" }.distinct().sorted()

    val availableTriggerTypes: List<String> =
        snapshots.mapNotNull { it.triggerType }.distinct().sorted()

    val availableLabels: List<String> =
        snapshots.flatMap { event ->
            event.detections.map { it.label } +
                listOfNotNull(event.triggerLabel, event.metadataLabel())
        }.distinct().sorted()

    val availableObjectLabels: List<String> = availableLabels.filter { !isSoundLabel(it) }
    val availableSoundLabels: List<String> = availableLabels.filter { isSoundLabel(it) }
}

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

    /** Saved scroll index so returning from PlayerScreen restores the list position. */
    var scrollIndex by mutableIntStateOf(0)
        private set

    fun saveScrollIndex(index: Int) {
        scrollIndex = index
    }

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
            val camerasResult = repository.cameras()
            
            if (eventsResult.isSuccess && camerasResult.isSuccess) {
                val events = eventsResult.getOrThrow()
                val cameras = camerasResult.getOrThrow()
                
                allSnapshots = events
                    .filter { it.hasSnapshot || !it.snapshotPath.isNullOrBlank() }
                
                val currentFilter = (_state.value as? SnapshotsUiState.Ready)?.data?.filter ?: SnapshotsFilter()
                _state.value = SnapshotsUiState.Ready(
                    SnapshotsReady(
                        snapshots = allSnapshots,
                        filtered = applyFilters(allSnapshots, currentFilter),
                        cameras = cameras,
                        filter = currentFilter,
                    ),
                )
            } else {
                val error = eventsResult.exceptionOrNull() ?: camerasResult.exceptionOrNull()
                _state.value = SnapshotsUiState.Error(error?.friendlyMessage() ?: "Unknown error")
            }
        }
    }

    fun setQuery(query: String) {
        updateFilter { it.copy(query = query) }
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        updateFilter { it.copy(dateStart = start, dateEnd = end) }
    }

    fun toggleCamera(cameraId: String) {
        updateFilter { current ->
            val selected = current.selectedCameras.toMutableSet()
            if (!selected.add(cameraId)) selected.remove(cameraId)
            current.copy(selectedCameras = selected)
        }
    }

    fun toggleMode(mode: String) {
        updateFilter { current ->
            val selected = current.selectedModes.toMutableSet()
            if (!selected.add(mode)) selected.remove(mode)
            current.copy(selectedModes = selected)
        }
    }

    fun toggleTriggerType(type: String) {
        updateFilter { current ->
            val selected = current.selectedTriggerTypes.toMutableSet()
            if (!selected.add(type)) selected.remove(type)
            current.copy(selectedTriggerTypes = selected)
        }
    }

    fun toggleLabel(label: String) {
        updateFilter { current ->
            val selected = current.selectedLabels.toMutableSet()
            if (!selected.add(label)) selected.remove(label)
            current.copy(selectedLabels = selected)
        }
    }

    fun setSortOrder(sortOrder: SnapshotsSortOrder) {
        updateFilter { it.copy(sortOrder = sortOrder) }
    }

    fun clearFilters() {
        updateFilter { SnapshotsFilter(dateStart = null, dateEnd = null) }
    }

    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)

    fun download(url: String, fileName: String) {
        viewModelScope.launch {
            downloader.downloadFile(url, fileName, "image/jpeg")
        }
    }

    private fun updateFilter(transform: (SnapshotsFilter) -> SnapshotsFilter) {
        _state.update { current ->
            if (current is SnapshotsUiState.Ready) {
                val filter = transform(current.data.filter)
                SnapshotsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(allSnapshots, filter)))
            } else current
        }
    }

    private fun applyFilters(events: List<Event>, filter: SnapshotsFilter): List<Event> {
        var result = events

        // Text search
        if (filter.query.isNotBlank()) {
            val q = filter.query.lowercase()
            result = result.filter { e ->
                e.detections.any { it.label.lowercase().contains(q) } ||
                    e.source?.lowercase()?.contains(q) == true ||
                    e.triggerLabel?.lowercase()?.contains(q) == true ||
                    e.triggerType?.lowercase()?.contains(q) == true ||
                    e.metadataLabel()?.lowercase()?.contains(q) == true
            }
        }

        // Date range
        if (filter.dateStart != null || filter.dateEnd != null) {
            val start = filter.dateStart?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime()
            val end = filter.dateEnd?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime()
            result = result.filter { e ->
                val ts = e.createdAt?.let {
                    try { OffsetDateTime.parse(it) } catch (_: Exception) { null }
                } ?: return@filter true
                if (start != null && ts.isBefore(start)) return@filter false
                if (end != null && !ts.isBefore(end)) return@filter false
                true
            }
        }

        // Mode
        if (filter.selectedModes.isNotEmpty()) {
            result = result.filter { e ->
                val isSound = e.source?.lowercase() == "sound" ||
                    e.triggerType?.lowercase() == "sound" ||
                    isSoundLabel(e.triggerLabel) ||
                    e.detections.any { isSoundLabel(it.label) }
                val isMotion = e.source?.lowercase() == "motion" ||
                    e.triggerType?.lowercase() == "motion" ||
                    isMotionLabel(e.triggerLabel) ||
                    e.detections.any { isMotionLabel(it.label) }
                val mode = when {
                    isSound -> "Sound"
                    isMotion -> "Motion"
                    else -> "Object"
                }
                mode in filter.selectedModes
            }
        }

        // Camera
        if (filter.selectedCameras.isNotEmpty()) {
            result = result.filter { e -> e.source in filter.selectedCameras }
        }

        // Trigger type
        if (filter.selectedTriggerTypes.isNotEmpty()) {
            result = result.filter { e -> e.triggerType in filter.selectedTriggerTypes }
        }

        // Label
        if (filter.selectedLabels.isNotEmpty()) {
            result = result.filter { e ->
                filter.selectedLabels.any { sel ->
                    e.detections.any { it.label == sel } ||
                        e.triggerLabel == sel ||
                        e.metadataLabel() == sel
                }
            }
        }

        // Sort
        result = when (filter.sortOrder) {
            SnapshotsSortOrder.NEWEST -> result.sortedByDescending { it.id }
            SnapshotsSortOrder.OLDEST -> result.sortedBy { it.id }
        }

        return result
    }
}

private fun Event.metadataLabel(): String? =
    (metadata["label"] as? JsonPrimitive)?.contentOrNull
