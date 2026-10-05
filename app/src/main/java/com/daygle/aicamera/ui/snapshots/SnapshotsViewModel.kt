package com.daygle.aicamera.ui.snapshots

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.FaceFacets
import com.daygle.aicamera.data.model.LibraryFacets
import com.daygle.aicamera.data.model.LibraryQuery
import com.daygle.aicamera.data.model.aiDescription
import com.daygle.aicamera.data.model.metadataLabel
import com.daygle.aicamera.ui.events.isMotionEvent
import com.daygle.aicamera.ui.events.isSoundEvent
import com.daygle.aicamera.ui.isSoundLabel
import com.daygle.aicamera.ui.library.LibraryPager
import com.daygle.aicamera.ui.library.endOfDayIso
import com.daygle.aicamera.ui.library.startOfDayIso
import com.daygle.aicamera.util.FileDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class SnapshotsSortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
}

/**
 * Filters for the Snapshots list. Search, time period, camera, label, face
 * and sort run on the server over the full history; camera, label and face
 * are single choices to match. Type and trigger apply to the loaded pages.
 */
data class SnapshotsFilter(
    val query: String = "",
    val dateStart: LocalDate? = LocalDate.now(),
    val dateEnd: LocalDate? = LocalDate.now(),
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val face: String? = null,
    val sortOrder: SnapshotsSortOrder = SnapshotsSortOrder.NEWEST,
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
        oldestFirst = sortOrder == SnapshotsSortOrder.OLDEST,
    )
}

data class SnapshotsReady(
    /** Every snapshot loaded so far for the current server-side filters. */
    val snapshots: List<Event>,
    /** [snapshots] after the in-app filters (type, trigger). */
    val filtered: List<Event>,
    val cameras: List<Camera> = emptyList(),
    val filter: SnapshotsFilter = SnapshotsFilter(),
    val refreshing: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    /** Label and face options for the time window; null on servers without facets. */
    val facets: LibraryFacets? = null,
) {
    val availableModes: List<String> = listOf("Object", "Sound", "Motion")

    /** Configured cameras, falling back to those seen in loaded snapshots. */
    val availableSources: List<String> by lazy {
        cameras.map { it.id }.ifEmpty {
            snapshots.mapNotNull { it.source }.filter { it != "sound" && it != "rtsp" }.distinct().sorted()
        }
    }

    val availableTriggerTypes: List<String> by lazy {
        snapshots.mapNotNull { it.triggerType }.distinct().sorted()
    }

    val availableLabels: List<String> by lazy {
        facets?.labels?.filter { !it.ai }?.map { it.value }
            ?: snapshots.flatMap { event ->
                event.detections.map { it.label } +
                    listOfNotNull(event.triggerLabel, event.metadataLabel())
            }.distinct().sorted()
    }

    /** Objects the AI model tagged on snapshots, apart from detector labels. */
    val availableAiTags: List<String> by lazy {
        facets?.labels?.filter { it.ai }?.map { it.value }
            ?: snapshots.flatMap { it.aiDescription()?.tags.orEmpty() }.distinct().filter { it !in availableLabels }.sorted()
    }

    val availableObjectLabels: List<String> by lazy { availableLabels.filter { !isSoundLabel(it) } }
    val availableSoundLabels: List<String> by lazy { availableLabels.filter { isSoundLabel(it) } }
    val faceFacets: FaceFacets? get() = facets?.faces
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

    private var filter = SnapshotsFilter()
    private var cameras: List<Camera> = emptyList()
    private var facets: LibraryFacets? = null

    private val pager = LibraryPager<Event>(viewModelScope, idOf = { it.id }) { publish() }
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
        reloadSnapshots()
    }

    fun loadMore() = pager.loadMore()

    private fun reloadSnapshots() {
        val query = filter.toQuery()
        pager.reload({ cursor -> repository.snapshotsPage(query, cursor) })
    }

    private fun loadFacets() {
        val query = filter.toQuery()
        facetsJob?.cancel()
        facetsJob = viewModelScope.launch {
            facets = repository.libraryFacets("snapshots", query.since, query.until).getOrNull()
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
            reloadSnapshots()
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

    fun setSortOrder(sortOrder: SnapshotsSortOrder) = updateFilter { it.copy(sortOrder = sortOrder) }

    fun clearFilters() {
        updateFilter { SnapshotsFilter(dateStart = null, dateEnd = null) }
        loadFacets()
    }

    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)

    /** Small capture-time thumbnail for list rows. */
    fun thumbnailUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId, thumbnail = true)

    fun download(url: String, fileName: String) {
        viewModelScope.launch {
            downloader.downloadFile(url, fileName, "image/jpeg")
        }
    }

    /** A filter the server applies: reload from the first page. */
    private fun updateFilter(transform: (SnapshotsFilter) -> SnapshotsFilter) {
        val next = transform(filter)
        if (next == filter) return
        queryJob?.cancel()
        filter = next
        publish()
        reloadSnapshots()
    }

    /** A filter applied to the loaded pages only. */
    private fun updateLocalFilter(transform: (SnapshotsFilter) -> SnapshotsFilter) {
        filter = transform(filter)
        publish()
    }

    private fun publish() {
        val page = pager.snapshot
        if (page.initialLoading && _state.value !is SnapshotsUiState.Ready) {
            _state.value = SnapshotsUiState.Loading
            return
        }
        val error = page.error
        if (error != null) {
            _state.value = SnapshotsUiState.Error(error)
            return
        }
        val filtered = applyLocalFilters(page.items, filter)
        _state.value = SnapshotsUiState.Ready(
            SnapshotsReady(
                snapshots = page.items,
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
        fun applyLocalFilters(events: List<Event>, filter: SnapshotsFilter): List<Event> {
            var result = events
            if (filter.selectedModes.isNotEmpty()) {
                result = result.filter { e ->
                    val mode = when {
                        isSoundEvent(e) -> "Sound"
                        isMotionEvent(e) -> "Motion"
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
