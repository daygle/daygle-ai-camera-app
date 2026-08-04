package com.daygle.aicamera.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.daygle.aicamera.data.model.Detection
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.formatTimestamp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    if (showFilterSheet) {
        EventsFilterSheet(
            state = (state as? EventsUiState.Ready)?.data?.filter ?: EventsFilter(),
            availableTriggerTypes = (state as? EventsUiState.Ready)?.data?.availableTriggerTypes ?: emptyList(),
            availableSources = (state as? EventsUiState.Ready)?.data?.availableSources ?: emptyList(),
            availableLabels = (state as? EventsUiState.Ready)?.data?.availableLabels ?: emptyList(),
            onDismiss = { showFilterSheet = false },
            viewModel = viewModel
        )
    }

    when (val s = state) {
        EventsUiState.Loading -> LoadingState(modifier)
        is EventsUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is EventsUiState.Ready -> {
            val data = s.data
            Column(modifier) {
                // Search & Filter Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = data.filter.query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search alerts...") },
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
                            )
                        )
                        
                        val activeFilterCount = data.filter.activeCount()
                        BadgedBox(
                            badge = {
                                if (activeFilterCount > 0) {
                                    Badge { Text(activeFilterCount.toString()) }
                                }
                            }
                        ) {
                            IconButton(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = "Filter")
                            }
                        }
                    }
                }

                // Quick Chip Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = data.filter.alertedOnly,
                        onClick = { viewModel.setAlertedOnly(!data.filter.alertedOnly) },
                        label = { Text("Alerts Only") },
                        leadingIcon = { 
                            if (data.filter.alertedOnly) {
                                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Active triggers
                    data.filter.selectedTriggerTypes.forEach { type ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.toggleTriggerType(type) },
                            label = { Text(formatEventLabel(type)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (data.filter.activeCount() > 0) {
                        TextButton(onClick = viewModel::clearFilters) {
                            Text("Reset", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // List count status
                if (data.events.size != data.filtered.size) {
                    Text(
                        "${data.filtered.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Event list
                val refreshing = data.refreshing
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = viewModel::load,
                    modifier = Modifier.weight(1f),
                ) {
                    if (data.filtered.isEmpty()) {
                        EmptyState(
                            if (data.filter.activeCount() > 0) "No events match your filters." else "No events recorded yet.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(data.filtered, key = { it.id }) { event ->
                                EventRow(event)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventsFilterSheet(
    state: EventsFilter,
    availableTriggerTypes: List<String>,
    availableSources: List<String>,
    availableLabels: List<String>,
    onDismiss: () -> Unit,
    viewModel: EventsViewModel
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.clearFilters(); onDismiss() }) {
                    Text("Clear all")
                }
            }

            // High Priority Toggle
            FilterSection(title = "Alerts", icon = Icons.Filled.NotificationsActive) {
                FilterChip(
                    selected = state.alertedOnly,
                    onClick = { viewModel.setAlertedOnly(!state.alertedOnly) },
                    label = { Text("Only show alerts") },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Trigger Types
            if (availableTriggerTypes.isNotEmpty()) {
                FilterSection(title = "Trigger type", icon = Icons.Filled.History) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTriggerTypes.forEach { type ->
                            FilterChip(
                                selected = type in state.selectedTriggerTypes,
                                onClick = { viewModel.toggleTriggerType(type) },
                                label = { Text(formatEventLabel(type)) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Cameras
            if (availableSources.isNotEmpty()) {
                FilterSection(title = "Cameras", icon = Icons.Filled.Videocam) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSources.forEach { source ->
                            FilterChip(
                                selected = source in state.selectedSources,
                                onClick = { viewModel.toggleSource(source) },
                                label = { Text(formatEventLabel(source)) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Labels
            if (availableLabels.isNotEmpty()) {
                FilterSection(title = "Detections", icon = Icons.Filled.FilterList) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableLabels.forEach { label ->
                            FilterChip(
                                selected = label in state.selectedLabels,
                                onClick = { viewModel.toggleLabel(label) },
                                label = { Text(formatEventLabel(label)) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Show Results")
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

private fun EventsFilter.activeCount(): Int {
    var count = 0
    if (query.isNotBlank()) count++
    if (alertedOnly) count++
    count += selectedSources.size
    count += selectedTriggerTypes.size
    count += selectedLabels.size
    return count
}

@Composable
private fun EventRow(event: Event) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    formatEventLabel(event.topLabel ?: event.source),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            formatTimestamp(event.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (event.source != null) {
                            Text(
                                formatEventLabel(event.source),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (event.source == "sound") Icons.Filled.Sensors else Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingContent = {
                if (event.alerted) {
                    AlertBadge()
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun AlertBadge() {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "ALERT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
