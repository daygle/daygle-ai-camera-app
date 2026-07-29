package com.daygle.aicamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daygle.aicamera.data.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StartDestination { LOADING, CONNECT, HOME }

class RootViewModel(private val repository: CameraRepository) : ViewModel() {

    private val _start = MutableStateFlow(StartDestination.LOADING)
    val start: StateFlow<StartDestination> = _start.asStateFlow()

    init {
        viewModelScope.launch {
            _start.value = if (repository.restore()) StartDestination.HOME else StartDestination.CONNECT
        }
    }

    fun disconnect(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.disconnect()
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RootViewModel(repository()) }
        }
    }
}
