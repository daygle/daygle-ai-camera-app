package com.daygle.aicamera.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.AppPreferencesStore
import com.daygle.aicamera.data.SettingsStore
import com.daygle.aicamera.data.ThemeMode
import com.daygle.aicamera.ui.HomeTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val use24Hour: Boolean = false,
    val refreshIntervalMs: Long = 1000L,
    /** The server this app is currently signed in to, shown in the Account card. */
    val serverLabel: String = "",
    val navItems: List<HomeTab> = HomeTab.entries,
) {
    val themeLabel: String get() = when (themeMode) {
        "dark" -> "Dark Mode"
        "light" -> "Light Mode"
        else -> "System Default"
    }

    val refreshLabel: String get() = when (refreshIntervalMs) {
        500L -> "Fast (500 ms)"
        1000L -> "Balanced (1 s)"
        else -> "Smooth (2 s)"
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val prefs: AppPreferencesStore,
    private val settingsStore: SettingsStore,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val connection = settingsStore.current()
            val savedNav = prefs.currentNavItems()?.split(",")?.mapNotNull { name ->
                runCatching { HomeTab.valueOf(name) }.getOrNull()
            } ?: HomeTab.entries

            _state.update {
                it.copy(
                    themeMode = prefs.currentThemeMode().key,
                    use24Hour = prefs.currentUse24Hour(),
                    refreshIntervalMs = prefs.currentRefreshIntervalMs(),
                    serverLabel = connection.baseUrl.toServerLabel(),
                    navItems = savedNav,
                )
            }
        }
    }

    fun setTheme(mode: String) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch { prefs.setThemeMode(ThemeMode.fromKey(mode)) }
    }

    fun setUse24Hour(value: Boolean) {
        _state.update { it.copy(use24Hour = value) }
        viewModelScope.launch { prefs.setUse24Hour(value) }
    }

    fun setRefreshInterval(ms: Long) {
        _state.update { it.copy(refreshIntervalMs = ms) }
        viewModelScope.launch { prefs.setRefreshIntervalMs(ms) }
    }

    fun moveNavItem(index: Int, up: Boolean) {
        val current = _state.value.navItems.toMutableList()
        val target = if (up) index - 1 else index + 1
        if (target in current.indices) {
            val item = current.removeAt(index)
            current.add(target, item)
            updateNavItems(current)
        }
    }

    fun toggleNavItem(tab: HomeTab) {
        val current = _state.value.navItems.toMutableList()
        if (current.contains(tab)) {
            if (current.size > 1) {
                current.remove(tab)
            }
        } else {
            current.add(tab)
        }
        updateNavItems(current)
    }

    private fun updateNavItems(items: List<HomeTab>) {
        _state.update { it.copy(navItems = items) }
        viewModelScope.launch {
            prefs.setNavItems(items.joinToString(",") { it.name })
        }
    }
}

/** Reduce a stored base URL to a compact host[:port] label for display. */
private fun String.toServerLabel(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ""
    val withoutScheme = trimmed.substringAfter("://", trimmed)
    return withoutScheme.substringBefore('/').ifBlank { trimmed }
}
