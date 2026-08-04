package com.daygle.aicamera.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.NotificationConfig
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.push.PushController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val topic: String = "",
    val username: String = "",
    val password: String = "",
    val discovering: Boolean = false,
    val message: String? = null,
) {
    val canEnable: Boolean get() = serverUrl.isNotBlank() && topic.isNotBlank()
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    application: Application,
    private val repository: CameraRepository,
    private val store: NotificationSettingsStore
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = store.current()
            _state.update {
                it.copy(
                    enabled = config.enabled,
                    serverUrl = config.serverUrl,
                    topic = config.topic,
                    username = config.username,
                    password = config.password,
                )
            }
        }
    }

    fun onServerUrl(value: String) = _state.update { it.copy(serverUrl = value, message = null) }
    fun onTopic(value: String) = _state.update { it.copy(topic = value, message = null) }
    fun onUsername(value: String) = _state.update { it.copy(username = value, message = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, message = null) }

    /** Pull the ntfy server/topic (and credentials, if the account is admin) from the server. */
    fun autofillFromServer() {
        _state.update { it.copy(discovering = true, message = null) }
        viewModelScope.launch {
            repository.pushSettings()
                .onSuccess { push ->
                    _state.update {
                        it.copy(
                            discovering = false,
                            serverUrl = push.serverUrl?.takeIf(String::isNotBlank) ?: it.serverUrl,
                            topic = push.topic?.takeIf(String::isNotBlank) ?: it.topic,
                            username = push.username?.takeIf(String::isNotBlank) ?: it.username,
                            password = push.password?.takeIf(String::isNotBlank) ?: it.password,
                            message = when {
                                push.serverUrl.isNullOrBlank() || push.topic.isNullOrBlank() ->
                                    "The server has no ntfy push configured yet. Set it up in the server's Settings first."
                                !push.enabled -> "Filled in from the server. Note: push is disabled server-side."
                                else -> "Filled in from the server."
                            },
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(discovering = false, message = "Couldn't read push settings from the server.")
                    }
                }
        }
    }

    /** Persist the config and start/stop the listener. [enabled] reflects the switch. */
    fun commit(enabled: Boolean, onSaved: (Boolean) -> Unit) {
        val s = _state.value
        val config = NotificationConfig(
            enabled = enabled && s.canEnable,
            serverUrl = s.serverUrl,
            topic = s.topic,
            username = s.username,
            password = s.password,
        )
        _state.update { it.copy(enabled = config.enabled) }
        viewModelScope.launch {
            store.save(config)
            PushController.sync(getApplication<Application>())
            onSaved(config.enabled)
        }
    }
}
