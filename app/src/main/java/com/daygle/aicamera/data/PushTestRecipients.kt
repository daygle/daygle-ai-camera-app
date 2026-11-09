package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.AppUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/** The signed-in admin and the server's users, for picking test push recipients. */
data class PushTestTargets(val meId: Int, val users: List<AppUser>)

/** One user a test push can go to: [value] is the `user:<id>` reference sent to the server. */
data class PushRecipientOption(val value: String, val name: String, val topic: String)

/**
 * The users a test push can reach, the same as the server's push picker:
 * active users with their own ntfy topic, sorted by name.
 */
internal fun pushRecipientOptions(users: List<AppUser>): List<PushRecipientOption> =
    users.filter { it.active && !it.ntfyTopic.isNullOrBlank() }
        .map { PushRecipientOption("user:${it.id}", it.displayName, it.ntfyTopic!!.trim()) }
        .sortedBy { it.name.lowercase() }

/** Tick the signed-in user to start with, when they can receive a push. */
internal fun defaultPushRecipients(options: List<PushRecipientOption>, meId: Int): Set<String> =
    options.map { it.value }.filter { it == "user:$meId" }.toSet()

/** The server's `{"detail": ...}` message from a failed request, if there is one. */
internal fun serverErrorDetail(error: Throwable): String? {
    val body = (error as? HttpException)?.response()?.errorBody()?.string() ?: return null
    return runCatching {
        Json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
