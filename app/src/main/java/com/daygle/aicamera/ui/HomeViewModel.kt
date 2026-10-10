package com.daygle.aicamera.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.AppPreferencesStore
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.NotificationSettingsStore
import com.daygle.aicamera.data.PushSettingsSync
import com.daygle.aicamera.push.PushController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferencesStore,
    repository: CameraRepository,
    notificationSettings: NotificationSettingsStore,
) : ViewModel() {

    private val pushSync = PushSettingsSync(repository, notificationSettings)

    val navItems: StateFlow<List<HomeTab>> = prefs.navItems.map(HomeTab::parse).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeTab.entries
    )

    /**
     * Pick up the server's current ntfy settings, then start or stop the alert
     * listener to match (restarting it on the new topic if it changed).
     */
    fun syncPush() {
        viewModelScope.launch {
            pushSync.refresh()
            PushController.sync(context)
        }
    }
}
