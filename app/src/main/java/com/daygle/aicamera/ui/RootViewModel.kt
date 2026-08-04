package com.daygle.aicamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { LOADING, ONBOARDING, CONNECT, HOME }

@HiltViewModel
class RootViewModel @Inject constructor(private val repository: CameraRepository) : ViewModel() {

    private val _start = MutableStateFlow(StartDestination.LOADING)
    val start: StateFlow<StartDestination> = _start.asStateFlow()

    init {
        viewModelScope.launch {
            val hasConnection = repository.restore()
            val needsOnboarding = !repository.currentSettingsStore().isOnboardingDone()
            _start.value = when {
                needsOnboarding -> StartDestination.ONBOARDING
                hasConnection -> StartDestination.HOME
                else -> StartDestination.CONNECT
            }
        }
    }

    fun onboardingComplete() {
        viewModelScope.launch {
            repository.currentSettingsStore().setOnboardingDone()
            _start.value = if (repository.restore()) StartDestination.HOME else StartDestination.CONNECT
        }
    }

    fun disconnect(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.disconnect()
            _start.value = StartDestination.CONNECT
            onDone()
        }
    }
}
