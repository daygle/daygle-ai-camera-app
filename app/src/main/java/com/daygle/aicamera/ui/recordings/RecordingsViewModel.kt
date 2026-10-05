package com.daygle.aicamera.ui.recordings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.FaceFacets
import com.daygle.aicamera.data.model.LibraryFacets
import com.daygle.aicamera.data.model.LibraryQuery
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.aiTags
import com.daygle.aicamera.ui.isMotion
import com.daygle.aicamera.ui.isSound
import com.daygle.aicamera.ui.isSoundLabel
import com.daygle.aicamera.ui.library.LibraryPager
import com.daygle.aicamera.ui.library.endOfDayIso
import com.daygle.aicamera.ui.library.startOfDayIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class SortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
}

/**
 * Filters for the Recordings list. Search, time period, camera, label, face
 * and sort run on the server over the full history; camera, label and face
 * are single choices to match. Type and trigger apply to the loaded pages.
 */
data class RecordingsFilter(
    val query: String = "",
    val dateStart: LocalDate? = LocalDate.now(),
    val dateEnd: LocalDate? = LocalDate.now(),
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val face: String? = null,
    val sortOrder: SortOrder = SortOrder.NEWEST,
) {
    fun activeCount(): Int {
        var count = 0
        if (query.isNotBlank()) count++
        count += selectedModes.size
        count += selectedCameras.size
        count += selectedTriggerTypes.size
        count += selectedLabels.size
        if (face != null) count++
        return count
    }

    fun toQuery(): LibraryQuery = LibraryQuery(
        query = query,
        since = startOfDayIso(dateStart),
        until = endOfDayIso(dateEnd),
        cameraId = selectedCameras.firstOrNull(),
        label = selectedLabels.firstOrNull(),
        face = face,
        oldestFirst = sortOrder == SortOrder.OLDEST,
    )
}

data class RecordingsReady(
    /** Every recording loaded so far for the current server-side filters. */
    val recordings: List<Recording>,
    /** [recordings] after the in-app filters (type, trigger). */
    val filtered: List<Recording>,
    val cameras: List<Camera> = emptyList(),
    val filter: RecordingsFilter = RecordingsFilter(),
    val refreshing: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    /** Label and face options for the time window; null on servers without facets. */
    val facets: LibraryFacets? = null,
) {
    val availableModes: List<String> = listOf("Object", "Sound", "Motion")

    // Derived collections are lazy: the Ready state is rebuilt on every change.

    /** Configured cameras, falling back to those seen in loaded recordings. */
    val availableCameras: List<String> by lazy {
        cameras.map { it.id }.ifEmpty {
            recordings.mapNotNull { it.source }.filter { it != "sound" && it != "rtsp" }.distinct().sorted()
        }
    }

    val availableTriggerTypes: List<String> by lazy {
        recordings.mapNotNull { it.triggerType }.distinct().sorted()
    }

    val availableLabels: List<String> by lazy {
        facets?.labels?.filter { !it.ai }?.map { it.value }
            ?: recordings.flatMap { it.labels }.distinct().sorted()
    }

    /** Objects the AI model tagged on clips, apart from detector labels. */
    val availableAiTags: List<String> by lazy {
        facets?.labels?.filter { it.ai }?.map { it.value }
            ?: recordings.flatMap { it.aiTags() }.distinct().filter { it !in availableLabels }.sorted()
    }

    val availableObjectLabels: List<String> by lazy { availableLabels.filter { !isSoundLabel(it) } }
    val availableSoundLabels: List<String> by lazy { availableLabels.filter { isSoundLabel(it) } }
    val faceFacets: FaceFacets? get() = facets?.faces
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

    private var filter = RecordingsFilter()
    private var cameras: List<Camera> = emptyList()
    private var facets: LibraryFacets? = null

    private val pager = LibraryPager<Recording>(viewModelScope, idOf = { it.id }) { publish() }
    private var queryJob: Job? = null
    private var facetsJob: Job? = null

    /** Saved scroll index so returning from PlayerScreen restores the list position. */
    var scrollIndex by mutableIntStateOf(0)
        private set

    fun saveScrollIndex(index: Int) {
        scrollIndex = index
    }

    init {
        load()
    }

    /** Reload cameras, filter options and the first page (pull-to-refresh). */
    fun load() {
        viewModelScope.launch {
            repository.cameras().onSuccess {
                cameras = it
                publish()
            }
        }
        loadFacets()
        reloadRecordings()
    }

    fun loadMore() = pager.loadMore()

    private fun reloadRecordings() {
        val query = filter.toQuery()
        pager.reload({ cursor -> repository.recordingsPage(query, cursor) })
    }

    private fun loadFacets() {
        val query = filter.toQuery()
        facetsJob?.cancel()
        facetsJob = viewModelScope.launch {
            facets = repository.libraryFacets("recordings", query.since, query.until).getOrNull()
            publish()
        }
    }

    /** Search runs on the server, so wait for a pause in typing before reloading. */
    fun setQuery(query: String) {
        filter = filter.copy(query = query)
        publish()
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reloadRecordings()
        }
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        updateFilter { it.copy(dateStart = start, dateEnd = end) }
        loadFacets()
    }

    fun toggleCamera(cameraId: String) = updateFilter {
        it.copy(selectedCameras = if (cameraId in it.selectedCameras) emptySet() else setOf(cameraId))
    }

    fun toggleMode(mode: String) = updateLocalFilter { current ->
        val selected = current.selectedModes.toMutableSet()
        if (!selected.add(mode)) selected.remove(mode)
        current.copy(selectedModes = selected)
    }

    fun toggleTriggerType(type: String) = updateLocalFilter { current ->
        val selected = current.selectedTriggerTypes.toMutableSet()
        if (!selected.add(type)) selected.remove(type)
        current.copy(selectedTriggerTypes = selected)
    }

    fun toggleLabel(label: String) = updateFilter {
        it.copy(selectedLabels = if (label in it.selectedLabels) emptySet() else setOf(label))
    }

    fun setFace(face: String?) = updateFilter { it.copy(face = face) }

    fun setSortOrder(sortOrder: SortOrder) = updateFilter { it.copy(sortOrder = sortOrder) }

    fun clearFilters() {
        updateFilter { RecordingsFilter(dateStart = null, dateEnd = null) }
        loadFacets()
    }

    /** Absolute URL for streaming or downloading a recording's MP4. */
    fun streamUrl(recordingId: Int): String? = repository.recordingStreamUrl(recordingId)

    /** A filter the server applies: reload from the first page. */
    private fun updateFilter(transform: (RecordingsFilter) -> RecordingsFilter) {
        val next = transform(filter)
        if (next == filter) return
        queryJob?.cancel()
        filter = next
        publish()
        reloadRecordings()
    }

    /** A filter applied to the loaded pages only. */
    private fun updateLocalFilter(transform: (RecordingsFilter) -> RecordingsFilter) {
        filter = transform(filter)
        publish()
    }

    private fun publish() {
        val page = pager.snapshot
        if (page.initialLoading && _state.value !is RecordingsUiState.Ready) {
            _state.value = RecordingsUiState.Loading
            return
        }
        val error = page.error
        if (error != null) {
            _state.value = RecordingsUiState.Error(error)
            return
        }
        val filtered = applyLocalFilters(page.items, filter)
        _state.value = RecordingsUiState.Ready(
            RecordingsReady(
                recordings = page.items,
                filtered = filtered,
                cameras = cameras,
                filter = filter,
                refreshing = page.refreshing || page.initialLoading,
                hasMore = page.hasMore,
                loadingMore = page.loadingMore,
                loadMoreError = page.loadMoreError,
                facets = facets,
            )
        )
        if (!page.initialLoading && !page.refreshing) pager.fillTo(filtered.size)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L

        /** The in-app filters (type and trigger) the server has no parameter for. */
        fun applyLocalFilters(recordings: List<Recording>, filter: RecordingsFilter): List<Recording> {
            var result = recordings
            if (filter.selectedModes.isNotEmpty()) {
                result = result.filter { r ->
                    val mode = when {
                        r.isSound() -> "Sound"
                        r.isMotion() -> "Motion"
                        else -> "Object"
                    }
                    mode in filter.selectedModes
                }
            }
            if (filter.selectedTriggerTypes.isNotEmpty()) {
                result = result.filter { it.triggerType in filter.selectedTriggerTypes }
            }
            return result
        }
    }
}
