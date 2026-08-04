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
                    it.copy(baseUrl = saved.baseUrl, username = saved.username, password = saved.password)
                }
            }
        }
    }

    fun onBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun onUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun connect(onSuccess: () -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val result = repository.connect(
                Connection(
                    baseUrl = current.baseUrl.trim(),
                    username = current.username.trim(),
                    password = current.password,
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
}
