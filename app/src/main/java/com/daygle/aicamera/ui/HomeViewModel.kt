package com.daygle.aicamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.AppPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val prefs: AppPreferencesStore
) : ViewModel() {

    val navItems: StateFlow<List<HomeTab>> = prefs.navItems.map { saved ->
        saved?.split(",")?.mapNotNull { name ->
            runCatching { HomeTab.valueOf(name) }.getOrNull()
        } ?: HomeTab.entries
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeTab.entries
    )
}
