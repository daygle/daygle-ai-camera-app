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
import kotlinx.coroutines.launch

sealed interface RecordingsUiState {
    data object Loading : RecordingsUiState
    data class Error(val message: String) : RecordingsUiState
    data class Ready(
        val recordings: List<Recording>,
        val refreshing: Boolean = false,
    ) : RecordingsUiState
}

class RecordingsViewModel(private val repository: CameraRepository) : ViewModel() {

    private val _state = MutableStateFlow<RecordingsUiState>(RecordingsUiState.Loading)
    val state: StateFlow<RecordingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val current = _state.value
        _state.value =
            if (current is RecordingsUiState.Ready) current.copy(refreshing = true) else RecordingsUiState.Loading
        viewModelScope.launch {
            repository.recordings()
                .onSuccess { _state.value = RecordingsUiState.Ready(it) }
                .onFailure { _state.value = RecordingsUiState.Error(it.friendlyMessage()) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RecordingsViewModel(repository()) }
        }
    }
}
