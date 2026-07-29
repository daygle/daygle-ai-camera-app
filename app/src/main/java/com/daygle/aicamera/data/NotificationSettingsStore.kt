package com.daygle.aicamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notifications")

/**
 * The ntfy subscription the app listens to for detection alerts. The server
 * publishes alerts to `{serverUrl}/{topic}`; the app subscribes to the same
 * topic's stream. Values are typically discovered from the server's push
 * settings but can be overridden here (e.g. when the server redacts the ntfy
 * password for a viewer account, or the topic is auth-protected).
 */
data class NotificationConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val topic: String = "",
    val username: String = "",
    val password: String = "",
) {
    /** Enough to open a subscription. */
    val isSubscribable: Boolean
        get() = enabled && serverUrl.isNotBlank() && topic.isNotBlank()
}

class NotificationSettingsStore(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("push_enabled")
        val SERVER_URL = stringPreferencesKey("ntfy_server_url")
        val TOPIC = stringPreferencesKey("ntfy_topic")
        val USERNAME = stringPreferencesKey("ntfy_username")
        val PASSWORD = stringPreferencesKey("ntfy_password")
    }

    val config: Flow<NotificationConfig> = context.notificationDataStore.data.map { prefs ->
        NotificationConfig(
            enabled = prefs[Keys.ENABLED] ?: false,
            serverUrl = prefs[Keys.SERVER_URL].orEmpty(),
            topic = prefs[Keys.TOPIC].orEmpty(),
            username = prefs[Keys.USERNAME].orEmpty(),
            password = prefs[Keys.PASSWORD].orEmpty(),
        )
    }

    suspend fun current(): NotificationConfig = config.first()

    suspend fun save(config: NotificationConfig) {
        context.notificationDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = config.enabled
            prefs[Keys.SERVER_URL] = config.serverUrl.trim()
            prefs[Keys.TOPIC] = config.topic.trim()
            prefs[Keys.USERNAME] = config.username.trim()
            prefs[Keys.PASSWORD] = config.password
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun clear() {
        context.notificationDataStore.edit { it.clear() }
    }
}
