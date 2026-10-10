package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.PushSettings

/**
 * Keeps the app's ntfy subscription in step with the Daygle server's push
 * settings (`GET /api/settings/alert-push`), so the server's dashboard is the
 * one place the ntfy server and topic are set. Runs on every launch; a
 * subscription the user set by hand ([NotificationConfig.followServer] false)
 * is left alone.
 */
class PushSettingsSync(
    private val repository: CameraRepository,
    private val store: NotificationSettingsStore,
) {
    /**
     * Re-read the server's push settings and store any change. Failures
     * (offline, older server) leave the subscription untouched.
     */
    suspend fun refresh() {
        val local = store.current()
        if (local.followServer == false) return
        val server = repository.pushSettings().getOrNull() ?: return
        followServerSettings(local, server)?.let { store.save(it) }
    }
}

/**
 * The subscription to store after reading the server's push [server] settings,
 * or null when nothing changes. The on/off switch stays the device's own
 * choice. A viewer account gets no ntfy password from the server, so a
 * password entered on the device is kept.
 *
 * An install from before following existed ([NotificationConfig.followServer]
 * null) follows the server only if its ntfy server and topic are empty or
 * already match; otherwise they were entered by hand and are kept.
 */
internal fun followServerSettings(local: NotificationConfig, server: PushSettings): NotificationConfig? {
    if (local.followServer == false) return null
    val serverUrl = server.serverUrl?.trim().orEmpty()
    val topic = server.topic?.trim().orEmpty()
    // The server has no ntfy push set up yet: nothing to follow.
    if (serverUrl.isEmpty() || topic.isEmpty()) return null
    if (local.followServer == null) {
        val unset = local.serverUrl.isBlank() && local.topic.isBlank()
        val matches = sameServerUrl(local.serverUrl, serverUrl) && local.topic.trim() == topic
        if (!unset && !matches) return local.copy(followServer = false)
    }
    val updated = local.copy(
        followServer = true,
        serverUrl = serverUrl,
        topic = topic,
        username = server.username?.trim()?.takeIf { it.isNotEmpty() } ?: local.username,
        password = server.password?.takeIf { it.isNotEmpty() } ?: local.password,
    )
    return updated.takeIf { it != local }
}

private fun sameServerUrl(a: String, b: String): Boolean =
    a.trim().trimEnd('/').equals(b.trim().trimEnd('/'), ignoreCase = true)
