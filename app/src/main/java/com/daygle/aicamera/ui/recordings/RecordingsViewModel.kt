package com.daygle.aicamera.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.Detection
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.friendlyMessage
import com.daygle.aicamera.ui.isMotionLabel
import com.daygle.aicamera.ui.isSoundLabel
import dagger.hilt.android.lifecycle.HiltViewModel
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

enum class SortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
}

data class RecordingsFilter(
    val query: String = "",
    val dateStart: LocalDate? = LocalDate.now(),
    val dateEnd: LocalDate? = LocalDate.now(),
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NEWEST,
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

data class RecordingsReady(
    val recordings: List<Recording>,
    val filtered: List<Recording>,
    val cameras: List<Camera> = emptyList(),
    val filter: RecordingsFilter = RecordingsFilter(),
    val refreshing: Boolean = false,
) {
    val availableModes: List<String> = listOf("Object", "Sound", "Motion")

    val availableCameras: List<String> =
        recordings.mapNotNull { it.source }.filter { it != "sound" && it != "rtsp" }.distinct().sorted()

    val availableTriggerTypes: List<String> =
        recordings.mapNotNull { it.triggerType }.distinct().sorted()

    val availableLabels: List<String> =
        recordings.flatMap { it.labels }.distinct().sorted()

    val availableObjectLabels: List<String> = availableLabels.filter { !isSoundLabel(it) }
    val availableSoundLabels: List<String> = availableLabels.filter { isSoundLabel(it) }
}

sealed interface RecordingsUiState {
    data object Loading : RecordingsUiState
    data class Error(val message: String) : RecordingsUiState
    data class Ready(val data: RecordingsReady) : RecordingsUiState
}

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val repository: CameraRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RecordingsUiState>(RecordingsUiState.Loading)
    val state: StateFlow<RecordingsUiState> = _state.asStateFlow()

    private var allRecordings: List<Recording> = emptyList()

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
        _state.value = if (current is RecordingsUiState.Ready)
            RecordingsUiState.Ready(current.data.copy(refreshing = true))
        else
            RecordingsUiState.Loading

        viewModelScope.launch {
            val recsRes = repository.recordings()
            val camerasRes = repository.cameras()

            if (recsRes.isSuccess && camerasRes.isSuccess) {
                val recordings = recsRes.getOrThrow()
                val cameras = camerasRes.getOrThrow()
                allRecordings = recordings
                val currentFilter = (_state.value as? RecordingsUiState.Ready)?.data?.filter ?: RecordingsFilter()
                _state.value = RecordingsUiState.Ready(
                    RecordingsReady(
                        recordings = recordings,
                        filtered = applyFilters(recordings, currentFilter),
                        cameras = cameras,
                        filter = currentFilter,
                    )
                )
            } else {
                val error = recsRes.exceptionOrNull() ?: camerasRes.exceptionOrNull()
                _state.value = RecordingsUiState.Error(error?.friendlyMessage() ?: "Unknown error")
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

    fun setSortOrder(sortOrder: SortOrder) {
        updateFilter { it.copy(sortOrder = sortOrder) }
    }

    fun clearFilters() {
        updateFilter { RecordingsFilter(dateStart = null, dateEnd = null) }
    }

    /** Absolute URL for streaming or downloading a recording's MP4. */
    fun streamUrl(recordingId: Int): String? = repository.recordingStreamUrl(recordingId)

    private fun updateFilter(transform: (RecordingsFilter) -> RecordingsFilter) {
        _state.update { current ->
            if (current is RecordingsUiState.Ready) {
                val filter = transform(current.data.filter)
                RecordingsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(allRecordings, filter)))
            } else current
        }
    }

    private fun applyFilters(recordings: List<Recording>, filter: RecordingsFilter): List<Recording> {
        var result = recordings

        // Text search on labels, source
        if (filter.query.isNotBlank()) {
            val q = filter.query.lowercase()
            result = result.filter { r ->
                r.labels.any { it.lowercase().contains(q) } ||
                    r.source?.lowercase()?.contains(q) == true ||
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
                } ?: return@filter true
                if (start != null && ts.isBefore(start)) return@filter false
                if (end != null && !ts.isBefore(end)) return@filter false
                true
            }
        }

        // Mode filter
        if (filter.selectedModes.isNotEmpty()) {
            result = result.filter { r ->
                val isSound = r.source?.lowercase() == "sound" ||
                        r.triggerType?.lowercase() == "sound" ||
                        isSoundLabel(r.triggerLabel) ||
                        r.labels.any { isSoundLabel(it) }
                val isMotion = r.source?.lowercase() == "motion" ||
                        r.triggerType?.lowercase() == "motion" ||
                        isMotionLabel(r.triggerLabel) ||
                        r.labels.any { isMotionLabel(it) }
                val mode = when {
                    isSound -> "Sound"
                    isMotion -> "Motion"
                    else -> "Object"
                }
                mode in filter.selectedModes
            }
        }

        // Camera filter
        if (filter.selectedCameras.isNotEmpty()) {
            result = result.filter { r -> r.source in filter.selectedCameras }
        }

        // Trigger type filter
        if (filter.selectedTriggerTypes.isNotEmpty()) {
            result = result.filter { r -> r.triggerType in filter.selectedTriggerTypes }
        }

        // Label filter
        if (filter.selectedLabels.isNotEmpty()) {
            result = result.filter { r ->
                filter.selectedLabels.any { it in r.labels }
            }
        }

        // Sort
        result = when (filter.sortOrder) {
            SortOrder.NEWEST -> result.sortedByDescending { it.id }
            SortOrder.OLDEST -> result.sortedBy { it.id }
        }

        return result
    }
}
