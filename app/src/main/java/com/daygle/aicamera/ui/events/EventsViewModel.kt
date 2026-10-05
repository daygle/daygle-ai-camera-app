package com.daygle.aicamera.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Camera
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.FaceFacets
import com.daygle.aicamera.data.model.LibraryFacets
import com.daygle.aicamera.data.model.LibraryQuery
import com.daygle.aicamera.data.model.SearchInterpretation
import com.daygle.aicamera.data.model.aiDescription
import com.daygle.aicamera.data.model.metadataLabel
import com.daygle.aicamera.ui.friendlyMessage
import com.daygle.aicamera.ui.isSoundLabel
import com.daygle.aicamera.ui.library.LibraryPager
import com.daygle.aicamera.ui.library.PagerSnapshot
import com.daygle.aicamera.ui.library.endOfDayIso
import com.daygle.aicamera.ui.library.startOfDayIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import javax.inject.Inject

enum class EventsSortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First")
}

/**
 * Filters for the Events list. Search, time period, camera, label, face,
 * alerts and sort run on the server over the full history (the same filters
 * as the web UI's library bar). Camera, label and face are single choices to
 * match. Type and trigger are applied to the loaded pages in the app, as the
 * web UI does.
 */
data class EventsFilter(
    val query: String = "",
    val dateStart: LocalDate? = null,
    val dateEnd: LocalDate? = null,
    val selectedModes: Set<String> = emptySet(),
    val selectedCameras: Set<String> = emptySet(),
    val selectedTriggerTypes: Set<String> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
    val face: String? = null,
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
        if (face != null) count++
        if (alertedOnly) count++
        return count
    }

    fun toQuery(): LibraryQuery = LibraryQuery(
        query = query,
        since = startOfDayIso(dateStart),
        until = endOfDayIso(dateEnd),
        cameraId = selectedCameras.firstOrNull(),
        label = selectedLabels.firstOrNull(),
        face = face,
        alertedOnly = alertedOnly,
        oldestFirst = sortOrder == EventsSortOrder.OLDEST,
    )
}

/**
 * A plain-English "Ask AI" search (`GET /api/event-search`). The server
 * interprets the question with its vision model (or a keyword fallback) and
 * searches the AI event descriptions and tags.
 */
data class AiSearchState(
    val query: String,
    val loading: Boolean = true,
    val results: List<Event> = emptyList(),
    val interpretation: SearchInterpretation? = null,
    val error: String? = null,
)

data class EventsReady(
    /** Every event loaded so far for the current server-side filters. */
    val events: List<Event>,
    /** [events] after the in-app filters (type, trigger). */
    val filtered: List<Event>,
    val cameras: List<Camera>,
    val filter: EventsFilter,
    val refreshing: Boolean = false,
    /** Non-null while an Ask AI search is shown in place of the filtered list. */
    val aiSearch: AiSearchState? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    /** Label and face options for the time window; null on servers without facets. */
    val facets: LibraryFacets? = null,
) {
    val availableModes: List<String> = listOf("Object", "Motion", "Sound", "Behaviour")

    /** Configured cameras, falling back to those seen in loaded events. */
    val availableSources: List<String> by lazy {
        cameras.map { it.id }.ifEmpty {
            events.mapNotNull { it.filterCameraId() }.filter { it != "sound" && it != "rtsp" }.distinct().sorted()
        }
    }

    val availableTriggerTypes: List<String> by lazy {
        events.mapNotNull { it.triggerType }.distinct().sorted()
    }

    /** Detection labels in the window (from the server's facets when available). */
    val availableLabels: List<String> by lazy {
        facets?.labels?.filter { !it.ai }?.map { it.value }
            ?: (events.flatMap { it.detections.map { d -> d.label } } +
                events.mapNotNull { it.triggerLabel } +
                events.mapNotNull { it.metadataLabel() }).distinct().sorted()
    }

    /** Objects the AI model tagged that the detector did not label. */
    val availableAiTags: List<String> by lazy {
        facets?.labels?.filter { it.ai }?.map { it.value }
            ?: events.flatMap { it.aiDescription()?.tags.orEmpty() }.distinct().sorted()
    }

    val availableObjectLabels by lazy { availableLabels.filter { !isSoundLabel(it) } }
    val availableSoundLabels by lazy { availableLabels.filter { isSoundLabel(it) } }
    val faceFacets: FaceFacets? get() = facets?.faces
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

    private var filter = EventsFilter()
    private var cameras: List<Camera> = emptyList()
    private var facets: LibraryFacets? = null
    private var aiSearch: AiSearchState? = null

    private val pager = LibraryPager<Event>(viewModelScope, idOf = { it.id }) { publish() }

    private var pollJob: Job? = null
    private var queryJob: Job? = null
    private var facetsJob: Job? = null
    private var aiSearchJob: Job? = null

    var scrollIndex: Int = 0
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
        reloadEvents()
    }

    fun loadMore() = pager.loadMore()

    /** Start periodic refresh of the newest events (called when the screen is resumed). */
    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                // Events change slowly compared to dashboard snapshots; a fixed
                // 30 s cadence keeps the feed current without hammering the
                // server. Only a newest-first list gains rows at its head.
                delay(POLL_INTERVAL_MS)
                if (filter.sortOrder == EventsSortOrder.NEWEST) pager.pollHead()
            }
        }
    }

    /** Stop periodic refresh (called when the screen is paused). */
    fun pausePolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun reloadEvents() {
        val query = filter.toQuery()
        pager.reload({ cursor -> repository.eventsPage(query, cursor) })
    }

    private fun loadFacets() {
        val query = filter.toQuery()
        facetsJob?.cancel()
        facetsJob = viewModelScope.launch {
            // Older servers have no facets endpoint: the sheet then lists the
            // labels seen in the loaded events instead.
            facets = repository.libraryFacets("events", query.since, query.until).getOrNull()
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
            reloadEvents()
        }
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        updateFilter { it.copy(dateStart = start, dateEnd = end) }
        loadFacets()
    }

    fun toggleCamera(camera: String) = updateFilter {
        it.copy(selectedCameras = if (camera in it.selectedCameras) emptySet() else setOf(camera))
    }

    fun toggleMode(mode: String) = updateLocalFilter {
        val new = it.selectedModes.toMutableSet()
        if (!new.remove(mode)) new.add(mode)
        it.copy(selectedModes = new)
    }

    fun toggleTriggerType(type: String) = updateLocalFilter {
        val new = it.selectedTriggerTypes.toMutableSet()
        if (!new.remove(type)) new.add(type)
        it.copy(selectedTriggerTypes = new)
    }

    fun toggleLabel(label: String) = updateFilter {
        it.copy(selectedLabels = if (label in it.selectedLabels) emptySet() else setOf(label))
    }

    fun setFace(face: String?) = updateFilter { it.copy(face = face) }

    fun setAlertedOnly(value: Boolean) = updateFilter { it.copy(alertedOnly = value) }

    fun setSortOrder(order: EventsSortOrder) = updateFilter { it.copy(sortOrder = order) }

    fun clearFilters() {
        val hadWindow = filter.dateStart != null || filter.dateEnd != null
        updateFilter { EventsFilter() }
        if (hadWindow) loadFacets()
    }

    /** Run a plain-English search; results replace the list until [clearAiSearch]. */
    fun runAiSearch(query: String) {
        val question = query.trim()
        if (question.isEmpty()) {
            clearAiSearch()
            return
        }
        aiSearchJob?.cancel()
        aiSearch = AiSearchState(query = question)
        publish()
        aiSearchJob = viewModelScope.launch {
            aiSearch = repository.searchEvents(question).fold(
                onSuccess = { response ->
                    AiSearchState(
                        query = question,
                        loading = false,
                        results = response.items,
                        interpretation = response.interpretation,
                    )
                },
                onFailure = { error ->
                    val message = if (error is HttpException && error.code() == 404) {
                        "AI search needs a newer Daygle server. Update the server to search events in plain English."
                    } else {
                        error.friendlyMessage()
                    }
                    AiSearchState(query = question, loading = false, error = message)
                },
            )
            publish()
        }
    }

    fun clearAiSearch() {
        aiSearchJob?.cancel()
        aiSearchJob = null
        aiSearch = null
        publish()
    }

    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)

    /** A filter the server applies: reload from the first page. */
    private fun updateFilter(block: (EventsFilter) -> EventsFilter) {
        val next = block(filter)
        if (next == filter) return
        queryJob?.cancel()
        filter = next
        publish()
        reloadEvents()
    }

    /** A filter applied to the loaded pages only. */
    private fun updateLocalFilter(block: (EventsFilter) -> EventsFilter) {
        filter = block(filter)
        publish()
    }

    private fun publish() {
        val page: PagerSnapshot<Event> = pager.snapshot
        if (page.initialLoading && _state.value !is EventsUiState.Ready) {
            _state.value = EventsUiState.Loading
            return
        }
        val error = page.error
        if (error != null) {
            _state.value = EventsUiState.Error(error)
            return
        }
        val filtered = applyLocalFilters(page.items, filter)
        _state.value = EventsUiState.Ready(
            EventsReady(
                events = page.items,
                filtered = filtered,
                cameras = cameras,
                filter = filter,
                refreshing = page.refreshing || page.initialLoading,
                aiSearch = aiSearch,
                hasMore = page.hasMore,
                loadingMore = page.loadingMore,
                loadMoreError = page.loadMoreError,
                facets = facets,
            )
        )
        if (!page.initialLoading && !page.refreshing) pager.fillTo(filtered.size)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val SEARCH_DEBOUNCE_MS = 400L

        /** The in-app filters (type and trigger) the server has no parameter for. */
        internal fun applyLocalFilters(events: List<Event>, filter: EventsFilter): List<Event> {
            var result = events
            if (filter.selectedModes.isNotEmpty()) {
                result = result.filter { event -> eventMode(event) in filter.selectedModes }
            }
            if (filter.selectedTriggerTypes.isNotEmpty()) {
                result = result.filter { it.triggerType in filter.selectedTriggerTypes }
            }
            return result
        }

        private fun eventMode(event: Event): String = when {
            isSoundEvent(event) -> "Sound"
            isMotionEvent(event) -> "Motion"
            event.isBehaviourEvent() -> "Behaviour"
            else -> "Object"
        }
    }
}
