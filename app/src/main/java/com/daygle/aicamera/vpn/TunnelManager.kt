package com.daygle.aicamera.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.daygle.aicamera.data.TunnelGate
import com.daygle.aicamera.data.WireGuardConfig
import com.daygle.aicamera.data.WireGuardConfigStore
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown by [TunnelManager.connect] when the user has not yet granted the
 *  one-time system VPN consent. The caller (an Activity) must launch
 *  [TunnelManager.consentIntent] to obtain it. */
class VpnConsentRequired : Exception("VPN permission has not been granted yet")

/**
 * Owns the app's embedded WireGuard tunnel.
 *
 * The tunnel is brought up through WireGuard's userspace [GoBackend] and is
 * scoped to this app's UID (via `IncludedApplications`), so when it is up the
 * OS forces *only this app's* traffic through WireGuard and leaves every other
 * app untouched. Combined with the fail-closed gate in the network layer (see
 * [TunnelGate]) this gives "VPN-only" behaviour with no traffic leaking when
 * the tunnel is down.
 *
 * Lifecycle is driven by the app, not the user: [ensureUp] is called when the
 * app comes to the foreground and by the push service so alerts keep flowing,
 * and [disconnect] runs when VPN-only mode is turned off. The one-time system
 * consent dialog ([consentIntent]) is the only manual step.
 */
@Singleton
class TunnelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: WireGuardConfigStore,
) : TunnelGate {

    enum class Status { DOWN, CONNECTING, UP, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // GoBackend loads the native wireguard-go library; build it lazily so a
    // load failure surfaces on first use rather than at graph construction.
    private val backend: Backend by lazy { GoBackend(context) }

    private val _status = MutableStateFlow(Status.DOWN)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Cached so the synchronous [TunnelGate] properties can be read from the
    // network interceptor without touching DataStore on the request path.
    @Volatile
    private var vpnOnlyCache = false

    override val vpnOnlyEnabled: Boolean get() = vpnOnlyCache
    override val tunnelUp: Boolean get() = _status.value == Status.UP

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            _status.value = if (newState == Tunnel.State.UP) Status.UP else Status.DOWN
        }
    }

    init {
        scope.launch { vpnOnlyCache = store.current().vpnOnly }
    }

    /** Non-null Intent when the user must still grant system VPN consent. */
    fun consentIntent(): Intent? = VpnService.prepare(context)

    suspend fun saveConfig(raw: String) = store.saveConfig(raw)

    suspend fun currentConfig(): WireGuardConfig = store.current()

    /** Turn VPN-only enforcement on or off. Disabling also tears the tunnel down. */
    suspend fun setVpnOnly(enabled: Boolean) {
        vpnOnlyCache = enabled
        store.setVpnOnly(enabled)
        if (!enabled) disconnect()
    }

    /** Bring the tunnel up. Fails with [VpnConsentRequired] if consent is missing. */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        val cfg = store.current()
        if (!cfg.hasConfig) {
            return@withContext Result.failure(IllegalStateException("No WireGuard configuration saved"))
        }
        if (consentIntent() != null) {
            return@withContext Result.failure(VpnConsentRequired())
        }
        _status.value = Status.CONNECTING
        try {
            val parsed = parseConfig(cfg.config)
            backend.setState(tunnel, Tunnel.State.UP, parsed)
            _status.value = Status.UP
            _lastError.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring tunnel up", e)
            _status.value = Status.ERROR
            _lastError.value = e.message ?: e::class.java.simpleName
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            .onFailure { Log.w(TAG, "Failed to bring tunnel down", it) }
        _status.value = Status.DOWN
        Unit
    }

    /**
     * Raise the tunnel if VPN-only mode is on and it isn't already up. Safe to
     * call from the app's foreground lifecycle and the push service; a missing
     * consent simply leaves the tunnel down (the gate then fails closed).
     */
    suspend fun ensureUp() {
        if (!vpnOnlyCache || tunnelUp) return
        connect().onFailure { Log.i(TAG, "ensureUp did not connect: ${it.message}") }
    }

    private fun parseConfig(raw: String): Config =
        BufferedReader(StringReader(withAppScope(raw))).use { Config.parse(it) }

    /**
     * Inject `IncludedApplications = <this app>` into the `[Interface]` section
     * so only this app's traffic uses the tunnel. Skipped if the user already
     * specified their own include/exclude list.
     */
    private fun withAppScope(raw: String): String {
        val lower = raw.lowercase()
        if ("includedapplications" in lower || "excludedapplications" in lower) return raw
        val lines = raw.lines().toMutableList()
        val ifaceIdx = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (ifaceIdx < 0) return raw
        lines.add(ifaceIdx + 1, "IncludedApplications = ${context.packageName}")
        return lines.joinToString("\n")
    }

    companion object {
        private const val TAG = "TunnelManager"
        private const val TUNNEL_NAME = "daygle"
    }
}
