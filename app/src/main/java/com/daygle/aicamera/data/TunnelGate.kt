package com.daygle.aicamera.data

import java.io.IOException

/**
 * A read-only view of VPN-only enforcement, queried by the network layer
 * before every request. Implemented by
 * [com.daygle.aicamera.vpn.TunnelManager].
 *
 * When [vpnOnlyEnabled] is true the app must not send any traffic unless the
 * WireGuard tunnel is [tunnelUp]. Because the tunnel is scoped to this app's
 * UID (see TunnelManager), an *up* tunnel forces all app traffic through
 * WireGuard at the OS level; the gate exists to *fail closed* when it is
 * *down*, so nothing ever leaks outside the tunnel.
 */
interface TunnelGate {
    /** True when the user has turned on VPN-only mode. */
    val vpnOnlyEnabled: Boolean

    /** True when the WireGuard tunnel is currently established. */
    val tunnelUp: Boolean
}

/**
 * Thrown by the fail-closed network interceptor when VPN-only mode is on but
 * the tunnel is not up. Extends [IOException] so OkHttp/Retrofit surface it as
 * an ordinary network failure that [toUserFriendlyMessage] can translate.
 */
class VpnRequiredException : IOException("VPN is required but not connected")
