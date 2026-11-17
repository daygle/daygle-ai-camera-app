package com.daygle.aicamera.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.AiInsightsResponse
import com.daygle.aicamera.data.model.ConfidenceSuggestion
import com.daygle.aicamera.data.model.IgnoreAreaSuggestion
import com.daygle.aicamera.data.model.IgnoredArea
import com.daygle.aicamera.data.serverErrorDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class AiInsightsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val insights: AiInsightsResponse? = null,
    /** An apply or remove is in flight; the buttons wait for it. */
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Admins only: the server's AI Insights, as on the web AI page. Each change
 * goes through the server's own validate-save-apply path and is audit-logged
 * there; the list is re-read afterwards.
 */
@HiltViewModel
class AiInsightsViewModel @Inject constructor(
    private val repository: CameraRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiInsightsUiState())
    val state: StateFlow<AiInsightsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.insights == null, error = null) }
        viewModelScope.launch {
            repository.aiInsights()
                .onSuccess { data -> _state.update { it.copy(loading = false, insights = data) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = loadErrorMessage(error)) } }
        }
    }

    /** The latest snapshot the AI rejected in a suggested spot. */
    fun eventThumbnailUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId, thumbnail = true)

    /** The camera's current frame, for spots without (or with a purged) snapshot. */
    fun cameraFrameUrl(cameraId: String): String? = repository.snapshotUrl(cameraId, cacheBuster = 0L)

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun apply(item: ConfidenceSuggestion) = change("Minimum confidence raised.") {
        repository.applyConfidenceSuggestion(item)
    }

    fun ignore(item: IgnoreAreaSuggestion) = change("Spot ignored.") { repository.applyIgnoreArea(item) }

    fun stopIgnoring(item: IgnoredArea) = change("No longer ignored.") { repository.removeIgnoreArea(item) }

    private fun change(done: String, block: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = block()
            _state.update {
                it.copy(
                    busy = false,
                    message = result.fold({ done }, { error -> serverErrorDetail(error) ?: "Couldn't save the change." }),
                )
            }
            if (result.isSuccess) load()
        }
    }
}

/** 403: not an admin; 404: a server from before AI Insights. */
internal fun loadErrorMessage(error: Throwable): String = when ((error as? HttpException)?.code()) {
    403 -> "Only administrators can see AI Insights."
    404 -> "This server doesn't have AI Insights yet. Update the Daygle server."
    else -> serverErrorDetail(error) ?: "Couldn't load AI Insights from the server."
}
