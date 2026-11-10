package com.daygle.aicamera.ui.notifications

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.NotificationConfig
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.data.RecipientChannel
import com.daygle.aicamera.data.defaultPushRecipients
import com.daygle.aicamera.data.model.AppUser
import com.daygle.aicamera.data.recipientOptions
import com.daygle.aicamera.data.serverErrorDetail
import com.daygle.aicamera.data.suspendRunCatching
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
    /** The ntfy server and topic follow the Daygle server's push settings. */
    val followServer: Boolean = true,
    /** The user's own topic from their Daygle profile (read-only here). */
    val userTopic: String = "",
    /** Admins can have the server send a test push through ntfy to chosen users. */
    val canServerTest: Boolean = false,
    /** The users the test push picker offers (the shared recipient picker). */
    val pushTestUsers: List<AppUser> = emptyList(),
    val pushTestSelected: Set<String> = emptySet(),
    val pushTestSending: Boolean = false,
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
            val config = suspendRunCatching { store.current() }.getOrElse {
                Log.w(TAG, "Could not read the alert settings", it)
                _state.update { s -> s.copy(message = "Couldn't read the saved alert settings.") }
                return@launch
            }
            _state.update {
                it.copy(
                    enabled = config.enabled,
                    serverUrl = config.serverUrl,
                    topic = config.topic,
                    username = config.username,
                    password = config.password,
                    followServer = config.followServer != false,
                    userTopic = config.userTopic,
                )
            }
        }
        loadPushTestTargets()
    }

    /** For an admin, the users a server-sent test push can go to. */
    private fun loadPushTestTargets() {
        viewModelScope.launch {
            val targets = repository.pushTestTargets().getOrNull() ?: return@launch
            _state.update {
                it.copy(
                    canServerTest = true,
                    pushTestUsers = targets.users,
                    pushTestSelected = defaultPushRecipients(targets.users, targets.meId),
                )
            }
        }
    }

    fun onPushTestRecipient(value: String, checked: Boolean) = _state.update {
        it.copy(pushTestSelected = if (checked) it.pushTestSelected + value else it.pushTestSelected - value)
    }

    /**
     * Have the server push a test notification through ntfy to the ticked
     * users' own topics, or the shared topic with nobody ticked. Unlike the
     * on-device test this checks the whole path, server to this phone.
     */
    fun sendServerTest() {
        val s = _state.value
        if (s.pushTestSending) return
        _state.update { it.copy(pushTestSending = true, message = null) }
        viewModelScope.launch {
            // Keep the picker's order, not the order the boxes were ticked.
            val recipients = recipientOptions(s.pushTestSelected.toList(), s.pushTestUsers, RecipientChannel.PUSH)
                .map { it.value }
                .filter { it in s.pushTestSelected }
            val result = repository.sendPushTest(recipients)
            _state.update {
                it.copy(
                    pushTestSending = false,
                    message = result.fold(
                        onSuccess = { topics -> "Test notification sent to ${topics.joinToString(", ")}." },
                        onFailure = { error -> serverErrorDetail(error) ?: "Couldn't send the test notification." },
                    ),
                )
            }
        }
    }

    /**
     * Switch between following the server's push settings and entering them
     * by hand. Turning it on reads them from the server straight away; either
     * way the choice is kept by the next save.
     */
    fun setFollowServer(follow: Boolean) {
        _state.update { it.copy(followServer = follow, message = null) }
        if (follow) autofillFromServer()
    }

    fun onServerUrl(value: String) = _state.update { it.copy(serverUrl = value, message = null) }
    fun onTopic(value: String) = _state.update { it.copy(topic = value, message = null) }
    fun onUsername(value: String) = _state.update { it.copy(username = value, message = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, message = null) }

    /** Pull the ntfy server/topic (and credentials, if the account is admin) from the server. */
    private fun autofillFromServer() {
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
                            userTopic = push.userTopic?.trim().orEmpty(),
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
            followServer = s.followServer,
            userTopic = s.userTopic,
        )
        _state.update { it.copy(enabled = config.enabled) }
        viewModelScope.launch {
            suspendRunCatching {
                store.save(config)
                PushController.sync(getApplication<Application>(), resync = true)
            }.onSuccess {
                onSaved(config.enabled)
            }.onFailure {
                Log.w(TAG, "Could not save the alert settings", it)
                _state.update { s -> s.copy(message = "Couldn't save the alert settings. Try again.") }
            }
        }
    }

    private companion object {
        const val TAG = "NotificationsViewModel"
    }
}
