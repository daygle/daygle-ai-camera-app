package com.daygle.aicamera.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connection")

private const val SECURE_PREFS_NAME = "connection_secrets"
private const val CF_ACCESS_CLIENT_ID_KEY = "cf_access_client_id"
private const val CF_ACCESS_CLIENT_SECRET_KEY = "cf_access_client_secret"
private const val CF_ACCESS_KEY_ALIAS = "daygle_cf_access_key"
private const val GCM_TAG_LENGTH_BITS = 128

/** Small Android Keystore-backed store for credentials that must not be kept in plaintext. */
private class CloudflareAccessSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)

    fun read(key: String): String? = preferences.getString(key, null)?.let(::decrypt)

    fun write(key: String, value: String) {
        preferences.edit().putString(key, encrypt(value)).apply()
    }

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val iv = ByteArray(buffer.int).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(CF_ACCESS_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    CF_ACCESS_KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()
    }
}

/** The saved connection details for the hosted Daygle AI Camera server. */
data class Connection(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    /** Optional Cloudflare Access service token (Client ID / Client Secret). */
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && username.isNotBlank()

    /** True when a Cloudflare Access service token is configured. */
    val hasCloudflareAccess: Boolean
        get() = cfAccessClientId.isNotBlank() && cfAccessClientSecret.isNotBlank()
}

/**
 * Persists the server URL and credentials. The Cloudflare service token is
 * stored separately in an AES-GCM encrypted preference backed by an Android
 * Keystore key. There is no token-based API on the server, so the
 * username/password are needed to re-establish a session when the server
 * expires the session cookie.
 */
class SettingsStore(private val context: Context) {

    private val appPrefsStore = AppPreferencesStore(context)
    private val cloudflareSecrets = CloudflareAccessSecretStore(context)

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
            cfAccessClientId = cloudflareSecrets.read(CF_ACCESS_CLIENT_ID_KEY).orEmpty(),
            cfAccessClientSecret = cloudflareSecrets.read(CF_ACCESS_CLIENT_SECRET_KEY).orEmpty(),
        )
    }

    suspend fun current(): Connection = connection.first()

    suspend fun save(connection: Connection) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = connection.baseUrl
            prefs[Keys.USERNAME] = connection.username
            prefs[Keys.PASSWORD] = connection.password
        }
        if (connection.cfAccessClientId.isBlank()) {
            cloudflareSecrets.remove(CF_ACCESS_CLIENT_ID_KEY)
        } else {
            cloudflareSecrets.write(CF_ACCESS_CLIENT_ID_KEY, connection.cfAccessClientId)
        }
        if (connection.cfAccessClientSecret.isBlank()) {
            cloudflareSecrets.remove(CF_ACCESS_CLIENT_SECRET_KEY)
        } else {
            cloudflareSecrets.write(CF_ACCESS_CLIENT_SECRET_KEY, connection.cfAccessClientSecret)
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
        cloudflareSecrets.remove(CF_ACCESS_CLIENT_ID_KEY)
        cloudflareSecrets.remove(CF_ACCESS_CLIENT_SECRET_KEY)
    }

    fun appPrefs(): AppPreferencesStore = appPrefsStore
}
