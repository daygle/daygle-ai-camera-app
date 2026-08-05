package com.daygle.aicamera.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

enum class HomeTab(val label: String, val icon: ImageVector) {
    Cameras("Cameras", Icons.Outlined.Videocam),
    Timeline("Timeline", Icons.Outlined.ViewTimeline),
    Events("Events", Icons.Outlined.Notifications),
    Clips("Clips", Icons.Outlined.VideoLibrary),
    Settings("Settings", Icons.Outlined.Settings),
}
