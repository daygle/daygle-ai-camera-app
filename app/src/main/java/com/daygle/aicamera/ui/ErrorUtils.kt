package com.daygle.aicamera.ui

import com.daygle.aicamera.data.CloudflareAccessRequiredException
import com.daygle.aicamera.data.toUserFriendlyMessage
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpRetryException

/**
 * Extension to provide user-friendly error messages from [Throwable]s,
 * centralizing error mapping for all ViewModels.
 */
fun Throwable.friendlyMessage(): String = when (this) {
    is CloudflareAccessRequiredException ->
        "Access was rejected. Sign in again to reconnect to your server."
    is HttpException -> httpStatusMessage(code())
    is HttpRetryException -> httpStatusMessage(responseCode())
    is IOException -> this.toUserFriendlyMessage()
    else -> message ?: "Something went wrong. Please try again."
}

/**
 * Maps an HTTP status code to a user-facing explanation.
 */
private fun httpStatusMessage(code: Int): String = when (code) {
    401, 403 -> "Access denied. You may need to sign in again."
    404 -> "The server couldn't be found at this address. It may be misconfigured."
    408 -> "The request timed out. Please try again."
    429 -> "Too many requests. Please wait a moment and try again."
    500 -> "The server ran into a problem. Please try again shortly."
    502, 503, 504 -> "The server is temporarily unavailable. Please try again shortly."
    // Cloudflare origin errors (520–530): the tunnel/server can't be reached.
    in 520..530 -> "Can't reach your camera server right now. It may be offline or still starting up."
    else -> "The server returned an error (HTTP $code). Please try again."
}
