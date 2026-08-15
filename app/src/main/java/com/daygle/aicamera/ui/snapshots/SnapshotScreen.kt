package com.daygle.aicamera.ui.snapshots

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.daygle.aicamera.ui.components.ZoomableImage

/**
 * Standalone full-screen viewer for one event's annotated snapshot. Reached by
 * tapping a push alert, and mirrors the snapshot dialog used from the events
 * and snapshots lists (pinch-to-zoom, pan, double-tap reset).
 */
@Composable
fun SnapshotScreen(
    eventId: Int,
    onBack: () -> Unit,
    viewModel: SnapshotViewModel = hiltViewModel(),
) {
    val url = remember(eventId) { viewModel.snapshotUrl(eventId) }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            ZoomableImage(
                model = url,
                contentDescription = "Event snapshot",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                "Snapshot unavailable",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}
