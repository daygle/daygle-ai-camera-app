package com.daygle.aicamera.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.ui.dashboard.DashboardScreen
import com.daygle.aicamera.ui.events.EventsScreen
import com.daygle.aicamera.ui.recordings.RecordingsScreen
import com.daygle.aicamera.ui.snapshots.SnapshotsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRecording: (Int) -> Unit,
    onOpenGeneralSettings: () -> Unit,
    onOpenNavigationSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenServerDetails: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navItems by viewModel.navItems.collectAsStateWithLifecycle()
    // rememberSaveable so the tab survives navigating to the player and back
    var selectedTab by rememberSaveable { mutableStateOf<HomeTab?>(null) }
    
    // Ensure selectedTab is valid for the current navItems
    val effectiveSelectedTab = selectedTab?.takeIf { it in navItems } ?: navItems.firstOrNull()
    
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isDashboardFullscreen by remember { mutableStateOf(false) }
    var fullscreenResetTrigger by remember { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.daygle.aicamera.push.PushController.sync(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(effectiveSelectedTab?.label ?: "") },
                navigationIcon = {
                    if (isDashboardFullscreen) {
                        IconButton(onClick = { fullscreenResetTrigger++ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (navItems.isNotEmpty()) {
                NavigationBar {
                    navItems.forEach { tab ->
                        NavigationBarItem(
                            selected = effectiveSelectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        
        when (effectiveSelectedTab) {
            HomeTab.Cameras -> DashboardScreen(
                modifier = contentModifier,
                refreshTrigger = refreshTrigger,
                resetTrigger = fullscreenResetTrigger,
                onFullscreenChanged = { isDashboardFullscreen = it },
            )
            HomeTab.Timeline -> com.daygle.aicamera.ui.timeline.TimelineScreen(
                onOpenRecording = onOpenRecording,
                modifier = contentModifier,
                refreshTrigger = refreshTrigger,
            )
            HomeTab.Events -> EventsScreen(
                onPlayRecording = onOpenRecording,
                modifier = contentModifier,
                refreshTrigger = refreshTrigger,
            )
            HomeTab.Clips -> ClipsScreen(
                onOpenRecording = onOpenRecording,
                modifier = contentModifier,
                refreshTrigger = refreshTrigger,
            )
            HomeTab.Settings -> com.daygle.aicamera.ui.settings.SettingsScreen(
                onSignOut = onSignOut,
                onOpenGeneral = onOpenGeneralSettings,
                onOpenNavigation = onOpenNavigationSettings,
                onOpenNotifications = onOpenNotifications,
                onOpenServerDetails = onOpenServerDetails,
                onOpenAbout = onOpenAbout,
                modifier = contentModifier,
            )
            null -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ClipsScreen(
    onOpenRecording: (Int) -> Unit,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
) {
    var selectedClipsTab by rememberSaveable { mutableStateOf(ClipsTab.Recordings) }

    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = selectedClipsTab.ordinal) {
            ClipsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedClipsTab == tab,
                    onClick = { selectedClipsTab = tab },
                    text = { Text(tab.label) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                )
            }
        }
        when (selectedClipsTab) {
            ClipsTab.Recordings -> RecordingsScreen(
                onPlay = onOpenRecording,
                modifier = Modifier.fillMaxSize(),
                refreshTrigger = refreshTrigger,
            )
            ClipsTab.Snapshots -> SnapshotsScreen(
                modifier = Modifier.fillMaxSize(),
                refreshTrigger = refreshTrigger,
            )
        }
    }
}

private enum class ClipsTab(val label: String, val icon: ImageVector) {
    Recordings("Recordings", Icons.Outlined.VideoLibrary),
    Snapshots("Snapshots", Icons.Outlined.Image),
}
