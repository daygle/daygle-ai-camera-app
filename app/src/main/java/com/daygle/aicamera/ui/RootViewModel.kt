package com.daygle.aicamera.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.push.PushController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { LOADING, ONBOARDING, CONNECT, HOME }

@HiltViewModel
class RootViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CameraRepository,
    private val notificationSettings: NotificationSettingsStore,
) : ViewModel() {

    private val _start = MutableStateFlow(StartDestination.LOADING)
    val start: StateFlow<StartDestination> = _start.asStateFlow()

    /** Whether a saved connection was restored at launch. */
    private var hasConnection = false

    init {
        viewModelScope.launch {
            hasConnection = repository.restore()
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
            // The session was already restored (and signed in) during init;
            // restoring again would only repeat the login round-trip.
            _start.value = if (hasConnection) StartDestination.HOME else StartDestination.CONNECT
        }
    }

    /**
     * Sign out: stop the alert listener and forget both the server connection
     * and its ntfy subscription. Clearing the subscription matters - otherwise
     * the boot receiver and keep-alive worker would restart the listener (with
     * the stored ntfy credentials) for a server the user has signed out of.
     */
    fun disconnect(onDone: () -> Unit) {
        PushController.stop(context)
        viewModelScope.launch {
            notificationSettings.clear()
            repository.disconnect()
            _start.value = StartDestination.CONNECT
            onDone()
        }
    }
}
