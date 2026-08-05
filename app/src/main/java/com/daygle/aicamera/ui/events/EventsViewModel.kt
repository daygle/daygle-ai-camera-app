package com.daygle.aicamera.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import com.daygle.aicamera.ui.isSoundLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

enum class EventsSortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
}

data class EventsFilter(
    val query: String = "",
    val dateStart: LocalDate? = LocalDate.now(),
    val dateEnd: LocalDate? = LocalDate.now(),
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val alertedOnly: Boolean = false,
    val sortOrder: EventsSortOrder = EventsSortOrder.NEWEST,
) {
    fun activeCount(): Int {
        var count = 0
        if (query.isNotBlank()) count++
        if (alertedOnly) count++
        count += selectedModes.size
        count += selectedCameras.size
        count += selectedTriggerTypes.size
        count += selectedLabels.size
        return count
    }
}

data class EventsReady(
    val events: List<Event>,
    val filtered: List<Event>,
    val cameras: List<Camera> = emptyList(),
    val filter: EventsFilter = EventsFilter(),
    val refreshing: Boolean = false,
) {
    val availableModes: List<String> = listOf("Object", "Sound")

    val availableSources: List<String> =
        events.mapNotNull { it.source }.filter { it != "sound" && it != "rtsp" }.distinct().sorted()

    val availableTriggerTypes: List<String> =
        events.mapNotNull { it.triggerType }.distinct().sorted()

    val availableLabels: List<String> =
        events.flatMap { event ->
            event.detections.map { it.label } +
                listOfNotNull(event.triggerLabel, event.metadataLabel())
        }.distinct().sorted()

    val availableObjectLabels: List<String> = availableLabels.filter { !isSoundLabel(it) }
    val availableSoundLabels: List<String> = availableLabels.filter { isSoundLabel(it) }
}

sealed interface EventsUiState {
    data object Loading : EventsUiState
    data class Error(val message: String) : EventsUiState
    data class Ready(val data: EventsReady) : EventsUiState
}

@HiltViewModel
class EventsViewModel @Inject constructor(private val repository: CameraRepository) : ViewModel() {

    private val _state = MutableStateFlow<EventsUiState>(EventsUiState.Loading)
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    private var allEvents: List<Event> = emptyList()

    init {
        load()
    }

    fun load() {
        val current = _state.value
        _state.value = if (current is EventsUiState.Ready)
            EventsUiState.Ready(current.data.copy(refreshing = true))
        else
            EventsUiState.Loading

        viewModelScope.launch {
            val eventsRes = repository.events()
            val camerasRes = repository.cameras()

            if (eventsRes.isSuccess && camerasRes.isSuccess) {
                val events = eventsRes.getOrThrow()
                val cameras = camerasRes.getOrThrow()
                allEvents = events
                val currentFilter = (_state.value as? EventsUiState.Ready)?.data?.filter ?: EventsFilter()
                _state.value = EventsUiState.Ready(
                    EventsReady(
                        events = events,
                        filtered = applyFilters(events, currentFilter),
                        cameras = cameras,
                        filter = currentFilter,
                    )
                )
            } else {
                val error = eventsRes.exceptionOrNull() ?: camerasRes.exceptionOrNull()
                _state.value = EventsUiState.Error(error?.friendlyMessage() ?: "Unknown error")
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

    fun setAlertedOnly(value: Boolean) {
        updateFilter { it.copy(alertedOnly = value) }
    }

    fun setSortOrder(sortOrder: EventsSortOrder) {
        updateFilter { it.copy(sortOrder = sortOrder) }
    }

    fun clearFilters() {
        updateFilter { EventsFilter(dateStart = null, dateEnd = null) }
    }

    /** Absolute URL for an event's annotated snapshot, or null if unconfigured. */
    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)

    private fun updateFilter(transform: (EventsFilter) -> EventsFilter) {
        _state.update { current ->
            if (current is EventsUiState.Ready) {
                val filter = transform(current.data.filter)
                EventsUiState.Ready(current.data.copy(filter = filter, filtered = applyFilters(allEvents, filter)))
            } else current
        }
    }

    private fun applyFilters(events: List<Event>, filter: EventsFilter): List<Event> {
        var result = events

        // Text search on labels, source
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

        // Date range filter
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

        // Mode filter
        if (filter.selectedModes.isNotEmpty()) {
            result = result.filter { e ->
                val isSound = e.source?.lowercase() == "sound" || 
                    e.triggerType?.lowercase() == "sound" || 
                    isSoundLabel(e.triggerLabel) || 
                    e.detections.any { isSoundLabel(it.label) }
                val mode = if (isSound) "Sound" else "Object"
                mode in filter.selectedModes
            }
        }

        // Camera filter
        if (filter.selectedCameras.isNotEmpty()) {
            result = result.filter { e -> e.source in filter.selectedCameras }
        }

        // Trigger type filter
        if (filter.selectedTriggerTypes.isNotEmpty()) {
            result = result.filter { e -> e.triggerType in filter.selectedTriggerTypes }
        }

        // Label filter
        if (filter.selectedLabels.isNotEmpty()) {
            result = result.filter { e ->
                filter.selectedLabels.any { sel ->
                    e.detections.any { it.label == sel } ||
                        e.triggerLabel == sel ||
                        e.metadataLabel() == sel
                }
            }
        }

        // Alerted only
        if (filter.alertedOnly) {
            result = result.filter { it.alerted }
        }

        // Sort
        result = when (filter.sortOrder) {
            EventsSortOrder.NEWEST -> result.sortedByDescending { it.id }
            EventsSortOrder.OLDEST -> result.sortedBy { it.id }
        }

        return result
    }
}

private fun Event.metadataLabel(): String? =
    (metadata["label"] as? JsonPrimitive)?.contentOrNull
