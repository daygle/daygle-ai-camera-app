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
    Settings("Settings", Icons.Outlined.Settings);

    companion object {
        /**
         * Parse the persisted comma-separated tab order. Unknown names (e.g. a
         * tab removed in an update) and duplicates are dropped; if nothing
         * valid remains, every tab is shown. [Settings] is always present
         * (appended if a saved order lacks it): it is the only route to the
         * navigation settings, notifications and sign-out, so a bar without
         * it would leave the user stuck.
         */
        fun parse(saved: String?): List<HomeTab> {
            val tabs = saved?.split(",")
                ?.mapNotNull { name -> entries.find { it.name == name.trim() } }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?: return entries
            return if (Settings in tabs) tabs else tabs + Settings
        }
    }
}
