package com.daygle.aicamera.ui.snapshots

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holder for the standalone event-snapshot screen shown when a push alert is
 * tapped. Builds the authenticated snapshot URL for one event id and loads the
 * event itself, so the screen can show what the AI model said about it.
 */
@HiltViewModel
class SnapshotViewModel @Inject constructor(
    private val repository: CameraRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    init {
        savedStateHandle.get<Int>("eventId")?.takeIf { it > 0 }?.let { id ->
            viewModelScope.launch {
                // Best effort: the snapshot still shows without the details.
                repository.event(id).onSuccess { _event.value = it }
            }
        }
    }

    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)
}
