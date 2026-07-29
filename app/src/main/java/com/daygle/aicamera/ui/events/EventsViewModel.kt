package com.daygle.aicamera.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import com.daygle.aicamera.ui.repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EventsUiState {
    data object Loading : EventsUiState
    data class Error(val message: String) : EventsUiState
    data class Ready(
        val events: List<Event>,
        val alertedOnly: Boolean,
        val refreshing: Boolean = false,
    ) : EventsUiState
}

class EventsViewModel(private val repository: CameraRepository) : ViewModel() {

    private val _state = MutableStateFlow<EventsUiState>(EventsUiState.Loading)
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    private var alertedOnly = false

    init {
        load()
    }

    fun setAlertedOnly(value: Boolean) {
        if (value == alertedOnly) return
        alertedOnly = value
        load()
    }

    fun load() {
        val current = _state.value
        _state.value = if (current is EventsUiState.Ready) current.copy(refreshing = true) else EventsUiState.Loading
        viewModelScope.launch {
            repository.events(alertedOnly = alertedOnly)
                .onSuccess { _state.value = EventsUiState.Ready(it, alertedOnly) }
                .onFailure { _state.value = EventsUiState.Error(it.friendlyMessage()) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { EventsViewModel(repository()) }
        }
    }
}
