package com.daygle.aicamera.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsFilter(
    val query: String = "",
    val selectedSources: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val alertedOnly: Boolean = false,
)

data class EventsReady(
    val events: List<Event>,
    val filtered: List<Event>,
    val filter: EventsFilter = EventsFilter(),
    val refreshing: Boolean = false,
) {
    val availableSources: List<String> =
        events.mapNotNull { it.source }.distinct().sorted()

    val availableLabels: List<String> =
        events.flatMap { it.detections.map { d -> d.label } + listOfNotNull(it.triggerType, it.triggerLabel) }
            .distinct().sorted()
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
            repository.events()
                .onSuccess { events ->
                    allEvents = events
                    val currentFilter = (_state.value as? EventsUiState.Ready)?.data?.filter ?: EventsFilter()
                    _state.value = EventsUiState.Ready(
                        EventsReady(
                            events = events,
                            filtered = applyFilters(events, currentFilter),
                            filter = currentFilter,
                        )
                    )
                }
                .onFailure { _state.value = EventsUiState.Error(it.friendlyMessage()) }
        }
    }

    fun setQuery(query: String) {
        updateFilter { it.copy(query = query) }
    }

    fun toggleSource(source: String) {
        updateFilter { current ->
            val selected = current.selectedSources.toMutableSet()
            if (!selected.add(source)) selected.remove(source)
            current.copy(selectedSources = selected)
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

    fun clearFilters() {
        updateFilter { EventsFilter() }
    }

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
                    e.triggerType?.lowercase()?.contains(q) == true
            }
        }

        // Source filter
        if (filter.selectedSources.isNotEmpty()) {
            result = result.filter { e -> e.source in filter.selectedSources }
        }

        // Label filter
        if (filter.selectedLabels.isNotEmpty()) {
            result = result.filter { e ->
                filter.selectedLabels.any { sel ->
                    e.detections.any { it.label == sel } ||
                        e.triggerLabel == sel ||
                        e.triggerType == sel
                }
            }
        }

        // Alerted only
        if (filter.alertedOnly) {
            result = result.filter { it.alerted }
        }

        return result
    }
}
