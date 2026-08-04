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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connection")

/** The saved connection details for the hosted Daygle AI Camera server. */
data class Connection(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && username.isNotBlank()
}

/**
 * Persists the server URL and credentials. Credentials are stored locally in
 * the app's private DataStore and excluded from cloud backup (see
 * `res/xml/backup_rules.xml`). There is no token-based API on the server, so
 * the username/password are needed to re-establish a session when the server
 * expires the session cookie.
 */
class SettingsStore(private val context: Context) {

    private val appPrefsStore = AppPreferencesStore(context)

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val connection: Flow<Connection> = context.dataStore.data.map { prefs ->
        Connection(
            baseUrl = prefs[Keys.BASE_URL].orEmpty(),
            username = prefs[Keys.USERNAME].orEmpty(),
            password = prefs[Keys.PASSWORD].orEmpty(),
        )
    }

    suspend fun current(): Connection = connection.first()

    suspend fun save(connection: Connection) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = connection.baseUrl
            prefs[Keys.USERNAME] = connection.username
            prefs[Keys.PASSWORD] = connection.password
        }
    }

    suspend fun isOnboardingDone(): Boolean =
        context.dataStore.data.map { prefs -> prefs[Keys.ONBOARDING_DONE] ?: false }.first()

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.BASE_URL)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.PASSWORD)
        }
    }

    fun appPrefs(): AppPreferencesStore = appPrefsStore
}
