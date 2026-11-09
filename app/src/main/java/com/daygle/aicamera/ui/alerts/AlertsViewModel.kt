package com.daygle.aicamera.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.AlertConfig
import com.daygle.aicamera.data.AlertSource
import com.daygle.aicamera.data.AlertTarget
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.RecipientChannel
import com.daygle.aicamera.data.alertTargets
import com.daygle.aicamera.data.serverErrorDetail
import com.daygle.aicamera.data.toRecipientRefs
import com.daygle.aicamera.data.withRecipients
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val loading: Boolean = true,
    /** The signed-in account isn't an admin: the server won't share alerts. */
    val notAdmin: Boolean = false,
    val error: String? = null,
    val config: AlertConfig? = null,
    val targets: List<AlertTarget> = emptyList(),
    /** Keys of alerts whose recipients changed since the last load or save. */
    val edited: Set<String> = emptySet(),
    val saving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: CameraRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlertsUiState())
    val state: StateFlow<AlertsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, message = null) }
        viewModelScope.launch {
            repository.alertConfig()
                .onSuccess { config ->
                    _state.update {
                        if (config == null) {
                            AlertsUiState(loading = false, notAdmin = true)
                        } else {
                            AlertsUiState(loading = false, config = config, targets = displayTargets(config))
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = serverErrorDetail(error) ?: "Couldn't load alerts from the server.") }
                }
        }
    }

    /** Old typed email addresses that belong to a user show (and save) as that user. */
    private fun displayTargets(config: AlertConfig): List<AlertTarget> =
        alertTargets(config.cameras, config.faceRules).map { it.copy(email = toRecipientRefs(it.email, config.users)) }

    fun onRecipient(key: String, channel: RecipientChannel, value: String, checked: Boolean) = _state.update { s ->
        s.copy(
            targets = s.targets.map { target ->
                if (target.key != key) return@map target
                val current = if (channel == RecipientChannel.EMAIL) target.email else target.push
                val next = if (checked) (current + value).distinct() else current - value
                if (channel == RecipientChannel.EMAIL) target.copy(email = next) else target.copy(push = next)
            },
            edited = s.edited + key,
            message = null,
        )
    }

    /** Write the edited alerts' recipients into the configs and send them to the server. */
    fun save() {
        val s = _state.value
        val config = s.config ?: return
        if (s.saving || s.edited.isEmpty()) return
        var cameras = config.cameras
        var faceRules = config.faceRules
        var camerasChanged = false
        var faceRulesChanged = false
        for (target in s.targets.filter { it.key in s.edited }) {
            when (target.source) {
                AlertSource.CAMERAS -> {
                    cameras = withRecipients(cameras, target.path, target.source, target.email, target.push)
                    camerasChanged = true
                }
                AlertSource.FACE_RULES -> {
                    faceRules = withRecipients(faceRules, target.path, target.source, target.email, target.push)
                    faceRulesChanged = true
                }
            }
        }
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            repository.saveAlertConfig(cameras.takeIf { camerasChanged }, faceRules.takeIf { faceRulesChanged })
                .onSuccess {
                    val saved = config.copy(cameras = cameras, faceRules = faceRules)
                    _state.update { it.copy(saving = false, config = saved, edited = emptySet(), message = "Alert recipients saved.") }
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false, message = serverErrorDetail(error) ?: "Couldn't save the alert recipients.") }
                }
        }
    }
}
