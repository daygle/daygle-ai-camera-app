package com.daygle.aicamera.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import com.daygle.aicamera.ui.isSoundLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

enum class SortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LONGEST("Longest"),
}

data class RecordingsFilter(
    val query: String = "",
    val dateStart: LocalDate? = null,
    val dateEnd: LocalDate? = null,
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NEWEST,
)

data class RecordingsReady(
    val recordings: List<Recording>,
    val filtered: List<Recording>,
    val cameras: List<Camera> = emptyList(),
    val filter: RecordingsFilter = RecordingsFilter(),
    val refreshing: Boolean = false,
) {
    val availableModes: List<String> = listOf("Object", "Sound")

    val availableCameras: List<String> =
        recordings.mapNotNull { it.cameraId ?: it.source }.filter { it != "sound" && it != "rtsp" }.distinct().sorted()

    val availableTriggerTypes: List<String> =
        recordings.mapNotNull { it.triggerType }.filter { it.lowercase() != "alert" }.distinct().sorted()

    val availableLabels: List<String> =
        recordings
            .flatMap { r -> r.labels + r.detections.map { it.label } + listOfNotNull(r.triggerLabel) }
            .distinct()
            .sorted()

    val availableObjectLabels: List<String> = availableLabels.filter { !isSoundLabel(it) }
    val availableSoundLabels: List<String> = availableLabels.filter { isSoundLabel(it) }
}

sealed interface RecordingsUiState {
    data object Loading : RecordingsUiState
    data class Error(val message: String) : RecordingsUiState
    data class Ready(val data: RecordingsReady) : RecordingsUiState
}

@HiltViewModel
class RecordingsViewModel @Inject constructor(private val repository: CameraRepository) : ViewModel() {

    private val _state = MutableStateFlow<RecordingsUiState>(RecordingsUiState.Loading)
    val state: StateFlow<RecordingsUiState> = _state.asStateFlow()

    private var allRecordings: List<Recording> = emptyList()

    init {
        load()
    }

    fun load() {
        val current = _state.value
        if (current is RecordingsUiState.Ready) {
            _state.value = RecordingsUiState.Ready(current.data.copy(refreshing = true))
        } else {
            _state.value = RecordingsUiState.Loading
        }
        viewModelScope.launch {
            val recordingsRes = repository.recordings()
            val camerasRes = repository.cameras()

            if (recordingsRes.isSuccess && camerasRes.isSuccess) {
                val recordings = recordingsRes.getOrThrow()
                val cameras = camerasRes.getOrThrow()
                allRecordings = recordings
                
                val currentFilter = (_state.value as? RecordingsUiState.Ready)?.data?.filter ?: RecordingsFilter()
                _state.value = RecordingsUiState.Ready(
                    RecordingsReady(
                        recordings = recordings,
                        filtered = applyFilters(currentFilter),
                        cameras = cameras,
                        filter = currentFilter,
                    )
                )
            } else {
                val error = recordingsRes.exceptionOrNull() ?: camerasRes.exceptionOrNull()
                _state.value = RecordingsUiState.Error(error?.friendlyMessage() ?: "Unknown error")
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val filter = current.data.filter.copy(query = query)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val filter = current.data.filter.copy(dateStart = start, dateEnd = end)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    fun toggleCamera(cameraId: String) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val selected = current.data.filter.selectedCameras.toMutableSet()
                if (!selected.add(cameraId)) selected.remove(cameraId)
                val filter = current.data.filter.copy(selectedCameras = selected)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    fun toggleMode(mode: String) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val selected = current.data.filter.selectedModes.toMutableSet()
                if (!selected.add(mode)) selected.remove(mode)
                val filter = current.data.filter.copy(selectedModes = selected)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    fun toggleTriggerType(type: String) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val selected = current.data.filter.selectedTriggerTypes.toMutableSet()
                if (!selected.add(type)) selected.remove(type)
                val filter = current.data.filter.copy(selectedTriggerTypes = selected)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    fun toggleLabel(label: String) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val selected = current.data.filter.selectedLabels.toMutableSet()
                if (!selected.add(label)) selected.remove(label)
                val filter = current.data.filter.copy(selectedLabels = selected)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val filter = current.data.filter.copy(sortOrder = sortOrder)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    fun clearFilters() {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val filter = RecordingsFilter()
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(filter)))
            } else current
        }
    }

    private fun applyFilters(filter: RecordingsFilter): List<Recording> {
        var result = allRecordings

        // Text search on labels, triggerLabel, triggerType
        if (filter.query.isNotBlank()) {
            val q = filter.query.lowercase()
            result = result.filter { r ->
                r.labels.any { it.lowercase().contains(q) } ||
                    r.detections.any { it.label.lowercase().contains(q) } ||
                    r.triggerLabel?.lowercase()?.contains(q) == true ||
                    r.triggerType?.lowercase()?.contains(q) == true
            }
        }

        // Date range filter
        if (filter.dateStart != null || filter.dateEnd != null) {
            val start = filter.dateStart?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime()
            val end = filter.dateEnd?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime()
            result = result.filter { r ->
                val ts = r.startedAt?.let {
                    try { OffsetDateTime.parse(it) } catch (_: Exception) { null }
                } ?: return@filter true // keep if no timestamp
                if (start != null && ts.isBefore(start)) return@filter false
                if (end != null && !ts.isBefore(end)) return@filter false
                true
            }
        }

        // Mode filter (Object vs Sound)
        if (filter.selectedModes.isNotEmpty()) {
            result = result.filter { r -> 
                val isSound = r.source?.lowercase() == "sound" || 
                    r.triggerType?.lowercase() == "sound" || 
                    isSoundLabel(r.triggerLabel) || 
                    r.labels.any { isSoundLabel(it) }
                val mode = if (isSound) "Sound" else "Object"
                mode in filter.selectedModes
            }
        }

        // Camera filter
        if (filter.selectedCameras.isNotEmpty()) {
            result = result.filter { r -> (r.cameraId ?: r.source) in filter.selectedCameras }
        }

        // Trigger type filter
        if (filter.selectedTriggerTypes.isNotEmpty()) {
            result = result.filter { r -> r.triggerType in filter.selectedTriggerTypes }
        }

        // Label filter
        if (filter.selectedLabels.isNotEmpty()) {
            result = result.filter { r ->
                filter.selectedLabels.any { sel ->
                    sel in r.labels || r.detections.any { it.label == sel } || sel == r.triggerLabel
                }
            }
        }

        // Sort
        result = when (filter.sortOrder) {
            SortOrder.NEWEST -> result.sortedByDescending { it.id }
            SortOrder.OLDEST -> result.sortedBy { it.id }
            SortOrder.LONGEST -> result.sortedByDescending { it.durationSeconds }
        }

        return result
    }
}
