package com.daygle.aicamera.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Cameras("Cameras", Icons.Outlined.Videocam),
    Events("Events", Icons.Outlined.Notifications),
    Recordings("Recordings", Icons.Outlined.VideoLibrary),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRecording: (Int) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(HomeTab.Cameras) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Resume the alert listener whenever the user is signed in and on this screen.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.daygle.aicamera.push.PushController.sync(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedTab.label) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Filled.NotificationsNone, contentDescription = "Alert notifications")
                    }
                },
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
            HomeTab.Recordings -> RecordingsScreen(
                onPlay = onOpenRecording,
                modifier = contentModifier,
                refreshTrigger = refreshTrigger,
            )
        }
    }
}
