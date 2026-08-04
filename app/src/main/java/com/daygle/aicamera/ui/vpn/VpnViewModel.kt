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

/** A transient banner shown under the configuration editor. */
data class VpnMessage(val text: String, val isError: Boolean)

data class VpnUiState(
    val vpnOnly: Boolean = false,
    val configText: String = "",
    val status: TunnelManager.Status = TunnelManager.Status.DOWN,
    val message: VpnMessage? = null,
) {
    val hasConfig: Boolean get() = configText.isNotBlank()

    /** Inline validation error for the current text, or null when it looks valid. */
    val validationError: String? get() = validateWireGuardConfig(configText)

    /** True when the text is present and passes basic structural validation. */
    val isValid: Boolean get() = hasConfig && validationError == null

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

    fun clearConfig() = _state.update { it.copy(configText = "", message = null) }

    /** The reason the config can't be used yet, or null when it's ready. */
    private fun blockingProblem(s: VpnUiState): String? =
        if (!s.hasConfig) "Paste a WireGuard configuration first." else s.validationError

    private fun reportProblem(problem: String) {
        _state.update { it.copy(message = VpnMessage(problem, isError = true)) }
    }

    fun saveConfig() {
        val current = _state.value
        blockingProblem(current)?.let { reportProblem(it); return }
        viewModelScope.launch {
            tunnelManager.saveConfig(current.configText)
            _state.update { it.copy(message = VpnMessage("Configuration saved.", isError = false)) }
        }
    }

    /** Turn on VPN-only mode: persist the config, then connect (asking for the
     *  one-time system consent via [onNeedConsent] if it hasn't been granted). */
    fun enableVpnOnly(onNeedConsent: (Intent) -> Unit) {
        val current = _state.value
        blockingProblem(current)?.let { reportProblem(it); return }
        viewModelScope.launch {
            tunnelManager.saveConfig(current.configText)
            tunnelManager.setVpnOnly(true)
            _state.update { it.copy(vpnOnly = true) }
            val consent = tunnelManager.consentIntent()
            if (consent != null) onNeedConsent(consent) else connectInternal()
        }
    }

    fun disableVpnOnly() {
        viewModelScope.launch {
            tunnelManager.setVpnOnly(false)
            _state.update {
                it.copy(
                    vpnOnly = false,
                    message = VpnMessage("VPN-only mode turned off.", isError = false),
                )
            }
        }
    }

    fun connect(onNeedConsent: (Intent) -> Unit) {
        val current = _state.value
        blockingProblem(current)?.let { reportProblem(it); return }
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
            _state.update {
                it.copy(
                    message = VpnMessage(
                        "VPN permission denied. VPN-only mode needs it to connect.",
                        isError = true,
                    ),
                )
            }
        }
    }

    private suspend fun connectInternal() {
        tunnelManager.connect()
            .onSuccess {
                _state.update { it.copy(message = VpnMessage("Tunnel connected.", isError = false)) }
            }
            .onFailure { e ->
                _state.update { it.copy(message = VpnMessage(e.toUserFriendlyMessage(), isError = true)) }
            }
    }
}

/**
 * Lightweight structural check so users get a clear, specific reason before we
 * ever hand the text to the WireGuard parser. Returns null when the text looks
 * like a usable tunnel configuration, otherwise a short human-readable reason.
 */
private fun validateWireGuardConfig(text: String): String? {
    if (text.isBlank()) return null // nothing entered yet; not an error to surface
    val lower = text.lowercase()
    fun hasKey(key: String) = Regex("(?im)^\\s*$key\\s*=\\s*\\S").containsMatchIn(text)

    return when {
        "[interface]" !in lower -> "Missing the [Interface] section."
        "[peer]" !in lower -> "Missing the [Peer] section."
        !hasKey("privatekey") -> "The [Interface] section needs a PrivateKey."
        !hasKey("address") -> "The [Interface] section needs an Address."
        !hasKey("publickey") -> "The [Peer] section needs a PublicKey."
        !hasKey("endpoint") -> "The [Peer] section needs an Endpoint (host:port)."
        else -> null
    }
}
