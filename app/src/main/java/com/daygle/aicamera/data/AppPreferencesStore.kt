package com.daygle.aicamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(key: String): ThemeMode = entries.find { it.key == key } ?: SYSTEM
    }
}

class AppPreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_24H = booleanPreferencesKey("use_24h")
        val REFRESH_INTERVAL_MS = longPreferencesKey("refresh_interval_ms")
        val NAV_ITEMS = stringPreferencesKey("nav_items")
    }

    val themeMode: Flow<ThemeMode> = context.appPrefsDataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.key)
    }

    val use24Hour: Flow<Boolean> = context.appPrefsDataStore.data.map { prefs ->
        prefs[Keys.USE_24H] ?: false
    }

    // Default matches the "Balanced (1 s)" option offered in Settings so a fresh
    // install shows a selection that reflects the real interval.
    val refreshIntervalMs: Flow<Long> = context.appPrefsDataStore.data.map { prefs ->
        prefs[Keys.REFRESH_INTERVAL_MS] ?: 1000L
    }

    val navItems: Flow<String?> = context.appPrefsDataStore.data.map { prefs ->
        prefs[Keys.NAV_ITEMS]
    }

    suspend fun currentThemeMode(): ThemeMode = themeMode.first()
    suspend fun currentUse24Hour(): Boolean = use24Hour.first()
    suspend fun currentRefreshIntervalMs(): Long = refreshIntervalMs.first()
    suspend fun currentNavItems(): String? = navItems.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appPrefsDataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setUse24Hour(value: Boolean) {
        context.appPrefsDataStore.edit { it[Keys.USE_24H] = value }
    }

    suspend fun setRefreshIntervalMs(value: Long) {
        context.appPrefsDataStore.edit { it[Keys.REFRESH_INTERVAL_MS] = value }
    }

    suspend fun setNavItems(value: String) {
        context.appPrefsDataStore.edit { it[Keys.NAV_ITEMS] = value }
    }
}
