package com.daygle.aicamera.ui.vpn

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.toUserFriendlyMessage
import com.daygle.aicamera.vpn.TunnelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VpnUiState(
    val vpnOnly: Boolean = false,
    val configText: String = "",
    val status: TunnelManager.Status = TunnelManager.Status.DOWN,
    val message: String? = null,
) {
    val hasConfig: Boolean get() = configText.isNotBlank()
    val endpoint: String? get() = configText.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("Endpoint", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

@HiltViewModel
class VpnViewModel @Inject constructor(
    private val tunnelManager: TunnelManager,
) : ViewModel() {

    private val _state = MutableStateFlow(VpnUiState())
    val state: StateFlow<VpnUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = tunnelManager.currentConfig()
            _state.update { it.copy(vpnOnly = cfg.vpnOnly, configText = cfg.config) }
        }
        viewModelScope.launch {
            tunnelManager.status.collect { s -> _state.update { it.copy(status = s) } }
        }
    }

    fun onConfigChange(text: String) = _state.update { it.copy(configText = text, message = null) }

    fun saveConfig() {
        viewModelScope.launch {
            tunnelManager.saveConfig(_state.value.configText)
            _state.update { it.copy(message = "Configuration saved.") }
        }
    }

    /** Turn on VPN-only mode: persist the config, then connect (asking for the
     *  one-time system consent via [onNeedConsent] if it hasn't been granted). */
    fun enableVpnOnly(onNeedConsent: (Intent) -> Unit) {
        val text = _state.value.configText
        if (text.isBlank()) {
            _state.update { it.copy(message = "Paste a WireGuard configuration first.") }
            return
        }
        viewModelScope.launch {
            tunnelManager.saveConfig(text)
            tunnelManager.setVpnOnly(true)
            _state.update { it.copy(vpnOnly = true) }
            val consent = tunnelManager.consentIntent()
            if (consent != null) onNeedConsent(consent) else connectInternal()
        }
    }

    fun disableVpnOnly() {
        viewModelScope.launch {
            tunnelManager.setVpnOnly(false)
            _state.update { it.copy(vpnOnly = false, message = "VPN-only mode turned off.") }
        }
    }

    fun connect(onNeedConsent: (Intent) -> Unit) {
        val consent = tunnelManager.consentIntent()
        if (consent != null) onNeedConsent(consent) else viewModelScope.launch { connectInternal() }
    }

    fun disconnect() {
        viewModelScope.launch { tunnelManager.disconnect() }
    }

    fun onConsentResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch { connectInternal() }
        } else {
            _state.update { it.copy(message = "VPN permission denied. VPN-only mode needs it to connect.") }
        }
    }

    private suspend fun connectInternal() {
        tunnelManager.connect()
            .onSuccess { _state.update { it.copy(message = "Tunnel connected.") } }
            .onFailure { e -> _state.update { it.copy(message = e.toUserFriendlyMessage()) } }
    }
}
