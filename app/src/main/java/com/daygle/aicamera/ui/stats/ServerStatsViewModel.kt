package com.daygle.aicamera.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.ActivityStats
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.ServerResources
import com.daygle.aicamera.data.model.StorageStatus
import com.daygle.aicamera.ui.friendlyMessage
import com.daygle.aicamera.ui.library.startOfDayIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class ActivityRange(val label: String) { TODAY("Today"), ALL_TIME("All time") }

data class ServerStatsUiState(
    /** True until the first refresh finishes. */
    val loading: Boolean = true,
    /** Pull-to-refresh is running. */
    val refreshing: Boolean = false,
    /** The account isn't an admin: the server only shares host stats with admins. */
    val notAdmin: Boolean = false,
    /** The last refresh failed; earlier numbers (if any) stay on screen. */
    val error: String? = null,
    val resources: ServerResources? = null,
    val health: CameraHealthResponse? = null,
    val activity: ActivityStats? = null,
    /** Disk space and media usage; null until loaded or on servers without it. */
    val storage: StorageStatus? = null,
    val range: ActivityRange = ActivityRange.TODAY,
    /** Device time of the last successful refresh. */
    val updatedAt: LocalTime? = null,
)

/**
 * Live server health for the Server Stats screen: host resources and camera
 * health every [POLL_INTERVAL_MS] (as the web System page), activity counts
 * every [ACTIVITY_INTERVAL_MS] and storage every [STORAGE_INTERVAL_MS] (the
 * server caches its folder sizes that long). Polls only while the screen is
 * resumed.
 */
@HiltViewModel
class ServerStatsViewModel @Inject constructor(
    private val repository: CameraRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerStatsUiState())
    val state: StateFlow<ServerStatsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var activityJob: Job? = null
    private var storageJob: Job? = null
    private var lastActivityAt = 0L
    private var lastStorageAt = 0L

    /** Start polling (the screen resumed). */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                refreshOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Stop polling (the screen paused or left). */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Pull-to-refresh: everything now, activity included. */
    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        lastActivityAt = 0L
        lastStorageAt = 0L
        viewModelScope.launch {
            refreshOnce()
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun setRange(range: ActivityRange) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range, activity = null) }
        loadActivity()
    }

    private suspend fun refreshOnce() {
        val resourcesCall = viewModelScope.async { repository.serverResources() }
        val healthCall = viewModelScope.async { repository.cameraHealth() }
        val resources = resourcesCall.await()
        val health = healthCall.await().getOrNull()
        val error = resources.exceptionOrNull()
        if (error is HttpException && (error.code() == 401 || error.code() == 403)) {
            // Camera health is open to every account, so still show it.
            _state.update { it.copy(loading = false, notAdmin = true, health = health ?: it.health, error = null) }
            return
        }
        _state.update { current ->
            current.copy(
                loading = false,
                notAdmin = false,
                resources = resources.getOrNull() ?: current.resources,
                health = health ?: current.health,
                error = error?.friendlyMessage(),
                updatedAt = if (error == null) LocalTime.now() else current.updatedAt,
            )
        }
        if (error == null && System.currentTimeMillis() - lastActivityAt >= ACTIVITY_INTERVAL_MS) loadActivity()
        if (error == null && System.currentTimeMillis() - lastStorageAt >= STORAGE_INTERVAL_MS) loadStorage()
    }

    private fun loadStorage() {
        lastStorageAt = System.currentTimeMillis()
        if (storageJob?.isActive == true) return
        storageJob = viewModelScope.launch {
            // Best effort: an older server has no storage endpoint (404) and
            // the card simply stays hidden; other failures keep the last value.
            repository.serverStorage().onSuccess { storage -> _state.update { it.copy(storage = storage) } }
        }
    }

    private fun loadActivity() {
        val range = _state.value.range
        lastActivityAt = System.currentTimeMillis()
        activityJob?.cancel()
        activityJob = viewModelScope.launch {
            val since = if (range == ActivityRange.TODAY) startOfDayIso(LocalDate.now()) else null
            // Best effort: the host tiles stand on their own without counts.
            repository.activityStats(since).onSuccess { stats ->
                _state.update { if (it.range == range) it.copy(activity = stats) else it }
            }
        }
    }

    private companion object {
        /** Matches the web System page's refresh. */
        const val POLL_INTERVAL_MS = 5_000L

        /** Counts change slowly and cost more to compute; refresh them less often. */
        const val ACTIVITY_INTERVAL_MS = 30_000L

        /** Matches the server's cache of folder sizes. */
        const val STORAGE_INTERVAL_MS = 60_000L
    }
}
