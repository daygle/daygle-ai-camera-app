package com.daygle.aicamera.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import com.daygle.aicamera.DaygleApp
import com.daygle.aicamera.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as DaygleApp).container.appPrefs

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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SettingsViewModel(app)
            }
        }
    }
}
