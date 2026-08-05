package com.daygle.aicamera.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.daygle.aicamera.ui.dashboard.DashboardScreen
import com.daygle.aicamera.ui.events.EventsScreen
import com.daygle.aicamera.ui.recordings.RecordingsScreen
import com.daygle.aicamera.ui.snapshots.SnapshotsScreen

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Cameras("Cameras", Icons.Outlined.Videocam),
    Events("Events", Icons.Outlined.Notifications),
    Clips("Clips", Icons.Outlined.VideoLibrary),
    Settings("Settings", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRecording: (Int) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenServerDetails: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(HomeTab.Cameras) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.daygle.aicamera.push.PushController.sync(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedTab.label) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (selectedTab) {
            HomeTab.Cameras -> DashboardScreen(
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
                onOpenNotifications = onOpenNotifications,
                onOpenServerDetails = onOpenServerDetails,
                onSignOut = onSignOut,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun ClipsScreen(
    onOpenRecording: (Int) -> Unit,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
) {
    var selectedClipsTab by remember { mutableStateOf(ClipsTab.Recordings) }

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
