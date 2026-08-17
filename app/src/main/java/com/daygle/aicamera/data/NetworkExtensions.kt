package com.daygle.aicamera.data

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps common OkHttp / java.net connection exceptions to user-friendly text.
 * The raw (technical) message is kept in parentheses for debugging.
 */
fun Throwable.toUserFriendlyMessage(): String {
    // Release builds block cleartext HTTP (network_security_config.xml). OkHttp
    // then throws a plain IOException ("Cleartext HTTP traffic to <host> not
    // permitted") that would otherwise fall through to the generic message,
    // leaving the user with no idea the address just needs to be https://.
    if (this is IOException && this !is CloudflareAccessRequiredException &&
        message?.contains("cleartext", ignoreCase = true) == true
    ) {
        return "This app requires a secure connection. Enter your server's https:// address " +
            "(release builds don't allow plain HTTP)."
    }
    return toUserFriendlyMessageInner()
}

private fun Throwable.toUserFriendlyMessageInner(): String = when (this) {
    is CloudflareAccessRequiredException ->
        "Cloudflare Access is blocking the connection. Check your Client ID and Client Secret in the connection settings."
    is UnknownHostException -> "Server not found. Check the address and your network connection."
    is SocketTimeoutException -> "Connection timed out. The server might be offline, or a firewall is blocking the port."
    is ConnectException -> "Connection refused. Make sure the server is running and the port is correct."
    is NoRouteToHostException -> "No route to host. The server cannot be reached on this network."
    is SSLException -> "SSL/TLS error. Check if your server supports HTTPS and has a valid certificate."
    else -> "Could not reach the server."
}

/**
 * A safe version of [runCatching] for coroutines that ensures [CancellationException]
 * is re-thrown so the coroutine scope can be correctly cancelled.
 */
inline fun <R> suspendRunCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
