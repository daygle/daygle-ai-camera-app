package com.daygle.aicamera.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.AppPreferencesStore
import com.daygle.aicamera.data.ThemeMode
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
    val refreshIntervalMs: Long = 700L,
) {
    val refreshLabel: String get() = when (refreshIntervalMs) {
        500L -> "Fast (500 ms)"
        1000L -> "Balanced (1 s)"
        else -> "Smooth (2 s)"
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val prefs: AppPreferencesStore
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    themeMode = prefs.currentThemeMode().key,
                    use24Hour = prefs.currentUse24Hour(),
                    refreshIntervalMs = prefs.currentRefreshIntervalMs(),
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
}
