package com.daygle.aicamera.data

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps common OkHttp / java.net connection exceptions to user-friendly text.
 * The raw (technical) message is kept in parentheses for debugging.
 */
fun Throwable.toUserFriendlyMessage(): String = when (this) {
    is CloudflareAccessRequiredException ->
        "Cloudflare Access is blocking the connection. Check your Client ID and Client Secret in the connection settings."
    is UnknownHostException -> "Server not found. Check the address and your network connection."
    is SocketTimeoutException -> "Connection timed out. The server might be offline, or a firewall is blocking the port."
    is ConnectException -> "Connection refused. Make sure the server is running and the port is correct."
    is NoRouteToHostException -> "No route to host. The server cannot be reached on this network."
    is SSLException -> "SSL/TLS error. Check if your server supports HTTPS and has a valid certificate."
    else -> "Could not reach the server."
}
