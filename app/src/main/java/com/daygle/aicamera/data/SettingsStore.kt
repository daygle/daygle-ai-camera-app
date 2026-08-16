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

private const val CF_ACCESS_CLIENT_ID_KEY = "cf_access_client_id"
private const val CF_ACCESS_CLIENT_SECRET_KEY = "cf_access_client_secret"
private const val CUSTOM_HEADER_VALUE_KEY = "custom_header_value"
private const val USERNAME_SECRET_KEY = "daygle_username"
private const val PASSWORD_SECRET_KEY = "daygle_password"

/** The saved connection details for the hosted Daygle AI Camera server. */
data class Connection(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    /** Optional Cloudflare Access service token (Client ID / Client Secret). */
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
    /** Optional custom HTTP header sent with every request (name stored in plain prefs, value encrypted). */
    val customHeaderName: String = "",
    val customHeaderValue: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && username.isNotBlank()
}

/**
 * Persists the server URL and credentials. Secrets are stored separately in
 * AES-GCM encrypted preferences backed by an Android Keystore key. There is no
 * token-based API on the server, so the username/password are needed to
 * re-establish a session when the server expires the session cookie.
 */
class SettingsStore(private val context: Context) {

    private val appPrefsStore = AppPreferencesStore(context)
    private val secrets = SecretStore(context)

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        // Retained only to migrate existing installations.
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CUSTOM_HEADER_NAME = stringPreferencesKey("custom_header_name")
    }

    val connection: Flow<Connection> = context.dataStore.data.map { prefs ->
        Connection(
            baseUrl = prefs[Keys.BASE_URL].orEmpty(),
            // Legacy fallback keeps old installations usable until restore()
            // performs the one-time encrypted migration.
            username = secrets.read(USERNAME_SECRET_KEY) ?: prefs[Keys.USERNAME].orEmpty(),
            password = secrets.read(PASSWORD_SECRET_KEY) ?: prefs[Keys.PASSWORD].orEmpty(),
            cfAccessClientId = secrets.read(CF_ACCESS_CLIENT_ID_KEY).orEmpty(),
            cfAccessClientSecret = secrets.read(CF_ACCESS_CLIENT_SECRET_KEY).orEmpty(),
            customHeaderName = prefs[Keys.CUSTOM_HEADER_NAME].orEmpty(),
            customHeaderValue = secrets.read(CUSTOM_HEADER_VALUE_KEY).orEmpty(),
        )
    }

    suspend fun current(): Connection = connection.first()

    /** Migrate legacy plaintext account credentials before restoring a session. */
    suspend fun migrateLegacyCredentials() {
        val legacy = context.dataStore.data.first()
        val username = legacy[Keys.USERNAME]
        val password = legacy[Keys.PASSWORD]
        if (username == null && password == null) return

        if (username != null) secrets.write(USERNAME_SECRET_KEY, username)
        if (password != null) secrets.write(PASSWORD_SECRET_KEY, password)
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.PASSWORD)
        }
    }

    suspend fun save(connection: Connection) {
        // Write encrypted copies first. If Keystore access fails, retain the
        // legacy values rather than losing the user's saved connection.
        secrets.write(USERNAME_SECRET_KEY, connection.username)
        secrets.write(PASSWORD_SECRET_KEY, connection.password)
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = connection.baseUrl
            prefs[Keys.CUSTOM_HEADER_NAME] = connection.customHeaderName.trim()
            // Username/password used to live in DataStore. Remove those legacy
            // values only after the encrypted copies have been written.
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.PASSWORD)
        }
        if (connection.cfAccessClientId.isBlank()) {
            secrets.remove(CF_ACCESS_CLIENT_ID_KEY)
        } else {
            secrets.write(CF_ACCESS_CLIENT_ID_KEY, connection.cfAccessClientId)
        }
        if (connection.cfAccessClientSecret.isBlank()) {
            secrets.remove(CF_ACCESS_CLIENT_SECRET_KEY)
        } else {
            secrets.write(CF_ACCESS_CLIENT_SECRET_KEY, connection.cfAccessClientSecret)
        }
        if (connection.customHeaderValue.isBlank()) {
            secrets.remove(CUSTOM_HEADER_VALUE_KEY)
        } else {
            secrets.write(CUSTOM_HEADER_VALUE_KEY, connection.customHeaderValue)
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
            prefs.remove(Keys.CUSTOM_HEADER_NAME)
        }
        secrets.remove(USERNAME_SECRET_KEY)
        secrets.remove(PASSWORD_SECRET_KEY)
        secrets.remove(CF_ACCESS_CLIENT_ID_KEY)
        secrets.remove(CF_ACCESS_CLIENT_SECRET_KEY)
        secrets.remove(CUSTOM_HEADER_VALUE_KEY)
    }

    fun appPrefs(): AppPreferencesStore = appPrefsStore
}
