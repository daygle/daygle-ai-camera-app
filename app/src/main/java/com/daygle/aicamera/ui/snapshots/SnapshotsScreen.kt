package com.daygle.aicamera.ui.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.daygle.aicamera.data.model.Detection
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.formatTimestamp
import com.daygle.aicamera.ui.isSoundLabel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Composable
fun SnapshotsScreen(
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: SnapshotsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openEventId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    openEventId?.let { eventId ->
        val url = viewModel.snapshotUrl(eventId)
        SnapshotDialog(
            url = url,
            onDismiss = { openEventId = null },
            onDownload = {
                url?.let {
                    val fileName = "snapshot-$eventId.jpg"
                    viewModel.download(it, fileName)
                }
            }
        )
    }

    when (val current = state) {
        SnapshotsUiState.Loading -> LoadingState(modifier)
        is SnapshotsUiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = modifier
        )
        is SnapshotsUiState.Ready -> {
            val data = current.data
            Column(modifier) {
                OutlinedTextField(
                    value = data.filter.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    placeholder = { Text("Search snapshots...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (data.filter.query.isNotBlank()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                )
                PullToRefreshBox(
                    isRefreshing = data.refreshing,
                    onRefresh = viewModel::load,
                    modifier = Modifier.weight(1f),
                ) {
                    if (data.filtered.isEmpty()) {
                        EmptyState(
                            if (data.filter.query.isBlank()) "No snapshots on the server yet." else "No snapshots match your search.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(data.filtered, key = { it.id }) { event ->
                                SnapshotRow(
                                    event = event,
                                    url = viewModel.snapshotUrl(event.id),
                                    onClick = { openEventId = event.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(
    event: Event,
    url: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = "Snapshot for event ${event.id}",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        formatEventLabel(event.topLabel ?: "Snapshot"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    EventTypeBadge(isSound = isSoundEvent(event))
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val isSound = isSoundEvent(event)
                    Icon(
                        if (isSound) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatTimestamp(event.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val detectionsToShow = if (event.detections.isEmpty() && event.source == "sound") {
                    val confidence = (event.metadata["confidence"] as? JsonPrimitive)?.doubleOrNull
                    val label = (event.metadata["label"] as? JsonPrimitive)?.contentOrNull
                    if (confidence != null && label != null) {
                        listOf(Detection(label, confidence))
                    } else {
                        emptyList()
                    }
                } else {
                    event.detections
                }

                if (detectionsToShow.isNotEmpty()) {
                    Text(
                        detectionsToShow.joinToString(", ") {
                            "${formatEventLabel(it.label)} (${(it.confidence * 100).toInt()}%)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EventTypeBadge(isSound: Boolean) {
    Surface(
        color = if (isSound) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(50),
    ) {
        Text(
            if (isSound) "Sound" else "Object",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSound) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}


private fun isSoundEvent(event: Event): Boolean =
    event.source?.lowercase() == "sound" ||
        event.triggerType?.lowercase() == "sound" ||
        isSoundLabel(event.triggerLabel) ||
        event.detections.any { isSoundLabel(it.label) }

@Composable
private fun SnapshotDialog(
    url: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = "Event snapshot",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Text("Snapshot unavailable", color = Color.White)
            }
        }
    }
}
