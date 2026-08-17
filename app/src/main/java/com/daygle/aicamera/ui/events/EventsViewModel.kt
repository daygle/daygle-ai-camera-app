package com.daygle.aicamera.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.friendlyMessage
import com.daygle.aicamera.ui.isMotionLabel
import com.daygle.aicamera.ui.isSoundLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

enum class EventsSortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First")
}

data class EventsFilter(
    val query: String = "",
    val dateStart: LocalDate? = null,
    val dateEnd: LocalDate? = null,
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val alertedOnly: Boolean = false,
    val sortOrder: EventsSortOrder = EventsSortOrder.NEWEST
) {
    fun activeCount(): Int {
        var count = 0
        if (query.isNotBlank()) count++
        if (dateStart != null || dateEnd != null) count++
        count += selectedModes.size
        count += selectedCameras.size
        count += selectedTriggerTypes.size
        count += selectedLabels.size
        if (alertedOnly) count++
        return count
    }
}

/** Pre-parsed event for faster filtering. */
private data class FilterableEvent(
    val event: Event,
    val timestamp: OffsetDateTime?,
    val isSound: Boolean,
    val isMotion: Boolean,
    val metadataLabel: String?
)

data class EventsReady(
    val events: List<Event>,
    val filtered: List<Event>,
    val cameras: List<Camera>,
    val filter: EventsFilter,
    val refreshing: Boolean = false,
) {
    val availableModes: List<String> = listOf("Object", "Motion", "Sound")
    
    val availableSources: List<String> by lazy {
        events.mapNotNull { it.source }.distinct().sorted()
    }
    
    val availableTriggerTypes: List<String> by lazy {
        events.mapNotNull { it.triggerType }.distinct().sorted()
    }
    
    val availableLabels: List<String> by lazy {
        (events.flatMap { it.detections.map { d -> d.label } } +
            events.mapNotNull { it.triggerLabel } +
            events.mapNotNull { it.metadataLabel() }).distinct().sorted()
    }
    
    val availableObjectLabels by lazy { availableLabels.filter { !isSoundLabel(it) } }
    val availableSoundLabels by lazy { availableLabels.filter { isSoundLabel(it) } }
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

    private var allFilterableEvents: List<FilterableEvent> = emptyList()
    
    var scrollIndex: Int = 0
        private set

    fun saveScrollIndex(index: Int) {
        scrollIndex = index
    }

    init {
        load()
    }

    fun load() {
        _state.update { if (it is EventsUiState.Ready) it.copy(data = it.data.copy(refreshing = true)) else EventsUiState.Loading }
        viewModelScope.launch {
            val eventsResult = repository.events()
            val camerasResult = repository.cameras()

            val events = eventsResult.getOrElse {
                _state.value = EventsUiState.Error(it.friendlyMessage())
                return@launch
            }
            val cameras = camerasResult.getOrDefault(emptyList())

            allFilterableEvents = events.map { e ->
                FilterableEvent(
                    event = e,
                    timestamp = e.createdAt?.let { try { OffsetDateTime.parse(it) } catch (_: Exception) { null } },
                    isSound = e.source?.lowercase() == "sound" || e.triggerType?.lowercase() == "sound" || 
                             isSoundLabel(e.triggerLabel) || e.detections.any { isSoundLabel(it.label) },
                    isMotion = e.source?.lowercase() == "motion" || e.triggerType?.lowercase() == "motion" || 
                              isMotionLabel(e.triggerLabel) || e.detections.any { isMotionLabel(it.label) },
                    metadataLabel = e.metadataLabel()
                )
            }

            val currentFilter = (_state.value as? EventsUiState.Ready)?.data?.filter ?: EventsFilter()
            _state.value = EventsUiState.Ready(
                EventsReady(
                    events = events,
                    filtered = applyFilters(allFilterableEvents, currentFilter),
                    cameras = cameras,
                    filter = currentFilter
                )
            )
        }
    }

    fun setQuery(query: String) = updateFilter { it.copy(query = query) }

    fun setDateRange(start: LocalDate?, end: LocalDate?) = updateFilter { it.copy(dateStart = start, dateEnd = end) }

    fun toggleCamera(camera: String) = updateFilter {
        val new = it.selectedCameras.toMutableSet()
        if (!new.remove(camera)) new.add(camera)
        it.copy(selectedCameras = new)
    }

    fun toggleMode(mode: String) = updateFilter {
        val new = it.selectedModes.toMutableSet()
        if (!new.remove(mode)) new.add(mode)
        it.copy(selectedModes = new)
    }

    fun toggleTriggerType(type: String) = updateFilter {
        val new = it.selectedTriggerTypes.toMutableSet()
        if (!new.remove(type)) new.add(type)
        it.copy(selectedTriggerTypes = new)
    }

    fun toggleLabel(label: String) = updateFilter {
        val new = it.selectedLabels.toMutableSet()
        if (!new.remove(label)) new.add(label)
        it.copy(selectedLabels = new)
    }

    fun setAlertedOnly(value: Boolean) = updateFilter { it.copy(alertedOnly = value) }

    fun setSortOrder(order: EventsSortOrder) = updateFilter { it.copy(sortOrder = order) }

    fun clearFilters() = updateFilter { EventsFilter() }

    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)

    private fun updateFilter(block: (EventsFilter) -> EventsFilter) {
        val current = (_state.value as? EventsUiState.Ready)?.data ?: return
        val nextFilter = block(current.filter)
        _state.value = EventsUiState.Ready(
            current.copy(
                filter = nextFilter,
                filtered = applyFilters(allFilterableEvents, nextFilter)
            )
        )
    }

    private fun applyFilters(filterable: List<FilterableEvent>, filter: EventsFilter): List<Event> {
        var seq = filterable.asSequence()

        if (filter.query.isNotBlank()) {
            val q = filter.query.lowercase()
            seq = seq.filter { fe ->
                fe.event.detections.any { it.label.lowercase().contains(q) } ||
                    fe.event.source?.lowercase()?.contains(q) == true ||
                    fe.event.triggerLabel?.lowercase()?.contains(q) == true ||
                    fe.event.triggerType?.lowercase()?.contains(q) == true ||
                    fe.metadataLabel?.lowercase()?.contains(q) == true
            }
        }

        if (filter.dateStart != null || filter.dateEnd != null) {
            val start = filter.dateStart?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime()
            val end = filter.dateEnd?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime()
            seq = seq.filter { fe ->
                val ts = fe.timestamp ?: return@filter true
                if (start != null && ts.isBefore(start)) return@filter false
                if (end != null && !ts.isBefore(end)) return@filter false
                true
            }
        }

        if (filter.selectedModes.isNotEmpty()) {
            seq = seq.filter { fe ->
                val mode = when {
                    fe.isSound -> "Sound"
                    fe.isMotion -> "Motion"
                    else -> "Object"
                }
                mode in filter.selectedModes
            }
        }

        if (filter.selectedCameras.isNotEmpty()) {
            seq = seq.filter { fe -> fe.event.source in filter.selectedCameras }
        }

        if (filter.selectedTriggerTypes.isNotEmpty()) {
            seq = seq.filter { fe -> fe.event.triggerType in filter.selectedTriggerTypes }
        }

        if (filter.selectedLabels.isNotEmpty()) {
            seq = seq.filter { fe ->
                filter.selectedLabels.any { sel ->
                    fe.event.detections.any { it.label == sel } ||
                        fe.event.triggerLabel == sel ||
                        fe.metadataLabel == sel
                }
            }
        }

        if (filter.alertedOnly) {
            seq = seq.filter { it.event.alerted }
        }

        val result = seq.map { it.event }.toList()

        return when (filter.sortOrder) {
            EventsSortOrder.NEWEST -> result.sortedByDescending { it.id }
            EventsSortOrder.OLDEST -> result.sortedBy { it.id }
        }
    }
}

private fun Event.metadataLabel(): String? =
    (metadata["label"] as? JsonPrimitive)?.contentOrNull
