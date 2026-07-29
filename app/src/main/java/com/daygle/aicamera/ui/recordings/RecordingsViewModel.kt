package com.daygle.aicamera.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import com.daygle.aicamera.ui.repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

enum class SortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LONGEST("Longest"),
}

data class RecordingsFilter(
    val query: String = "",
    val dateStart: LocalDate? = null,
    val dateEnd: LocalDate? = null,
    val selectedLabels: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NEWEST,
)

data class RecordingsReady(
    val recordings: List<Recording>,
    val filtered: List<Recording>,
    val filter: RecordingsFilter = RecordingsFilter(),
    val refreshing: Boolean = false,
) {
    val availableLabels: List<String> =
        recordings
            .flatMap { r -> r.labels + listOfNotNull(r.triggerType, r.triggerLabel) }
            .distinct()
            .sorted()
}

sealed interface RecordingsUiState {
    data object Loading : RecordingsUiState
    data class Error(val message: String) : RecordingsUiState
    data class Ready(val data: RecordingsReady) : RecordingsUiState
}

class RecordingsViewModel(private val repository: CameraRepository) : ViewModel() {

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
            repository.recordings()
                .onSuccess { recordings ->
                    allRecordings = recordings
                    _state.value = RecordingsUiState.Ready(
                        RecordingsReady(
                            recordings = recordings,
                            filtered = recordings,
                        )
                    )
                }
                .onFailure { _state.value = RecordingsUiState.Error(it.friendlyMessage()) }
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

        // Label / trigger type filter
        if (filter.selectedLabels.isNotEmpty()) {
            result = result.filter { r ->
                filter.selectedLabels.any { sel ->
                    sel in r.labels || sel == r.triggerType || sel == r.triggerLabel
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

    companion object {
        val Factory = viewModelFactory {
            initializer { RecordingsViewModel(repository()) }
        }
    }
}
