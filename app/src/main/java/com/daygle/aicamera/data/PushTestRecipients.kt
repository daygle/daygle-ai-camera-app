package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.AppUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/** The signed-in admin and the server's users, for picking test push recipients. */
data class PushTestTargets(val meId: Int, val users: List<AppUser>)

/**
 * Tick the signed-in user to start with, when they can receive a push: active
 * and with their own ntfy topic. The rows themselves come from the shared
 * picker ([recipientOptions] with [RecipientChannel.PUSH]).
 */
internal fun defaultPushRecipients(users: List<AppUser>, meId: Int): Set<String> =
    users.filter { it.id == meId && it.active && !it.ntfyTopic.isNullOrBlank() }
        .map { "user:${it.id}" }
        .toSet()

/** The server's `{"detail": ...}` message from a failed request, if there is one. */
internal fun serverErrorDetail(error: Throwable): String? {
    val body = (error as? HttpException)?.response()?.errorBody()?.string() ?: return null
    return runCatching {
        Json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
