package com.daygle.aicamera.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.Connection
import com.daygle.aicamera.data.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
    val customHeaderName: String = "",
    val customHeaderValue: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = baseUrl.isNotBlank() && username.isNotBlank() && !connecting
}

@HiltViewModel
class ConnectViewModel @Inject constructor(private val repository: CameraRepository) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.currentConnection()
            if (saved.baseUrl.isNotBlank() || saved.username.isNotBlank()) {
                _state.update {
                    it.copy(
                        baseUrl = saved.baseUrl,
                        username = saved.username,
                        password = saved.password,
                        cfAccessClientId = saved.cfAccessClientId,
                        cfAccessClientSecret = saved.cfAccessClientSecret,
                        customHeaderName = saved.customHeaderName,
                        customHeaderValue = saved.customHeaderValue,
                    )
                }
            }
        }
    }

    fun onBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun onUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onCfAccessClientId(value: String) = _state.update { it.copy(cfAccessClientId = value, error = null) }
    fun onCfAccessClientSecret(value: String) = _state.update { it.copy(cfAccessClientSecret = value, error = null) }
    fun onCustomHeaderName(value: String) = _state.update { it.copy(customHeaderName = value, error = null) }
    fun onCustomHeaderValue(value: String) = _state.update { it.copy(customHeaderValue = value, error = null) }

    fun connect(onSuccess: () -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        val headerName = current.customHeaderName.trim()
        if (headerName.isNotBlank() && !isValidHeaderName(headerName)) {
            _state.update {
                it.copy(error = "Invalid header name. Use letters, digits, and characters like -_.")
            }
            return
        }
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val result = repository.connect(
                Connection(
                    baseUrl = current.baseUrl.trim(),
                    username = current.username.trim(),
                    password = current.password,
                    cfAccessClientId = current.cfAccessClientId.trim(),
                    cfAccessClientSecret = current.cfAccessClientSecret,
                    customHeaderName = headerName,
                    customHeaderValue = current.customHeaderValue,
                ),
            )
            when (result) {
                LoginResult.Success -> {
                    _state.update { it.copy(connecting = false) }
                    onSuccess()
                }
                LoginResult.InvalidCredentials ->
                    _state.update { it.copy(connecting = false, error = "Invalid username or password.") }
                LoginResult.NotConfigured ->
                    _state.update { it.copy(connecting = false, error = "Enter a server address and username.") }
                is LoginResult.Error ->
                    _state.update { it.copy(connecting = false, error = result.message) }
            }
        }
    }

    companion object {
        // HTTP token characters allowed in a header name (RFC 7230).
        private val HEADER_NAME = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$")

        fun isValidHeaderName(name: String): Boolean = name.matches(HEADER_NAME)
    }
}
