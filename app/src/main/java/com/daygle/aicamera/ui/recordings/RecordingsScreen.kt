package com.daygle.aicamera.ui.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.formatDuration
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.formatTimestamp
import com.daygle.aicamera.ui.isSoundLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    if (showFilterSheet) {
        RecordingsFilterSheet(
            state = (state as? RecordingsUiState.Ready)?.data?.filter ?: RecordingsFilter(),
            availableModes = (state as? RecordingsUiState.Ready)?.data?.availableModes ?: emptyList(),
            availableCameras = (state as? RecordingsUiState.Ready)?.data?.availableCameras ?: emptyList(),
            availableTriggerTypes = (state as? RecordingsUiState.Ready)?.data?.availableTriggerTypes ?: emptyList(),
            availableObjectLabels = (state as? RecordingsUiState.Ready)?.data?.availableObjectLabels ?: emptyList(),
            availableSoundLabels = (state as? RecordingsUiState.Ready)?.data?.availableSoundLabels ?: emptyList(),
            cameraMap = (state as? RecordingsUiState.Ready)?.data?.cameras?.associate { it.id to it.displayName } ?: emptyMap(),
            onDismiss = { showFilterSheet = false },
            viewModel = viewModel
        )
    }

    when (val s = state) {
        RecordingsUiState.Loading -> LoadingState(modifier)
        is RecordingsUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is RecordingsUiState.Ready -> {
            val data = s.data
            val activeFilterCount = data.filter.activeCount()
            
            Column(modifier) {
                // Modern Search & Filter Bar
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
                            placeholder = { Text("Search recordings...") },
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

                // Quick Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort order quick view
                    AssistChip(
                        onClick = { showFilterSheet = true },
                        label = { Text(data.filter.sortOrder.label) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Active modes (Sound/Object)
                    data.filter.selectedModes.forEach { mode ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.toggleMode(mode) },
                            label = { Text(mode) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Date range if active
                    if (data.filter.dateStart != null || data.filter.dateEnd != null) {
                        AssistChip(
                            onClick = { showFilterSheet = true },
                            label = { Text(dateRangeLabel(data.filter.dateStart, data.filter.dateEnd)) },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)) },
                            trailingIcon = { 
                                Icon(
                                    Icons.Filled.Clear, 
                                    null, 
                                    Modifier.size(16.dp).clickable { viewModel.setDateRange(null, null) }
                                ) 
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    if (activeFilterCount > 0) {
                        TextButton(onClick = viewModel::clearFilters) {
                            Text("Reset", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // List count status
                if (data.recordings.size != data.filtered.size) {
                    Text(
                        "${data.filtered.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Recording list
                PullToRefreshBox(
                    isRefreshing = data.refreshing,
                    onRefresh = viewModel::load,
                    modifier = Modifier.weight(1f),
                ) {
                    if (data.filtered.isEmpty()) {
                        EmptyState(
                            if (activeFilterCount > 0) "No recordings match your filters." else "No recordings on the server yet.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(data.filtered, key = { it.id }) { recording ->
                                RecordingRow(recording, onPlay = { onPlay(recording.id) })
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
private fun RecordingsFilterSheet(
    state: RecordingsFilter,
    availableModes: List<String>,
    availableCameras: List<String>,
    availableTriggerTypes: List<String>,
    availableObjectLabels: List<String>,
    availableSoundLabels: List<String>,
    cameraMap: Map<String, String>,
    onDismiss: () -> Unit,
    viewModel: RecordingsViewModel
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.dateStart?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = state.dateEnd?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    val end = pickerState.selectedEndDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    viewModel.setDateRange(start, end)
                    showDatePicker = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DateRangePicker(state = pickerState)
        }
    }

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
                    Text("Clear All")
                }
            }

            // Sort By
            FilterSection(title = "Sort By", icon = Icons.AutoMirrored.Filled.Sort) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortOrder.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sortOrder == sort,
                            onClick = { viewModel.setSortOrder(sort) },
                            label = { Text(sort.label) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Type (Object vs Sound)
            FilterSection(title = "Detection Type", icon = Icons.Filled.History) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableModes.forEach { mode ->
                        FilterChip(
                            selected = mode in state.selectedModes,
                            onClick = { viewModel.toggleMode(mode) },
                            label = { Text(mode) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Date Range
            FilterSection(title = "Time Period", icon = Icons.Filled.CalendarMonth) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(dateRangeLabel(state.dateStart, state.dateEnd)) },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Trigger Types
            if (availableTriggerTypes.isNotEmpty()) {
                FilterSection(title = "Specific Trigger", icon = Icons.Filled.History) {
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
            if (availableCameras.isNotEmpty()) {
                FilterSection(title = "Cameras", icon = Icons.Filled.Videocam) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCameras.forEach { cam ->
                            FilterChip(
                                selected = cam in state.selectedCameras,
                                onClick = { viewModel.toggleCamera(cam) },
                                label = { Text(cameraMap[cam] ?: formatEventLabel(cam)) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Labels
            if (availableObjectLabels.isNotEmpty()) {
                FilterSection(title = "Object Activity", icon = Icons.Filled.FilterList) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableObjectLabels.forEach { label ->
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

            if (availableSoundLabels.isNotEmpty()) {
                FilterSection(title = "Sound Activity", icon = Icons.Filled.GraphicEq) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSoundLabels.forEach { label ->
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

private fun RecordingsFilter.activeCount(): Int {
    var count = 0
    if (query.isNotBlank()) count++
    if (dateStart != null || dateEnd != null) count++
    count += selectedModes.size
    count += selectedCameras.size
    count += selectedTriggerTypes.size
    count += selectedLabels.size
    return count
}

private fun getDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())

private fun dateRangeLabel(start: LocalDate?, end: LocalDate?): String {
    if (start == null && end == null) return "Any Time"
    val formatter = getDateFormatter()
    if (start != null && end != null) {
        return "${start.format(formatter)} - ${end.format(formatter)}"
    }
    return if (start != null) "From ${start.format(formatter)}" else "Until ${end!!.format(formatter)}"
}

@Composable
private fun RecordingRow(recording: Recording, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        enabled = recording.mediaReady,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    recording.title(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isSound = recording.source?.lowercase() == "sound" || 
                                recording.triggerType?.lowercase() == "sound" || 
                                isSoundLabel(recording.triggerLabel) || 
                                recording.labels.any { isSoundLabel(it) }
                        Icon(
                            imageVector = if (isSound) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            recording.subtitle(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val detectionsToShow = if (recording.detections.isEmpty() && recording.labelConfidences.isNotEmpty()) {
                        recording.labelConfidences.map { (label, confidence) ->
                            Detection(label, confidence)
                        }
                    } else {
                        recording.detections
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
            },
            leadingContent = {
                val isSound = recording.source?.lowercase() == "sound" || 
                        recording.triggerType?.lowercase() == "sound" || 
                        isSoundLabel(recording.triggerLabel) || 
                        recording.labels.any { isSoundLabel(it) }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSound) MaterialTheme.colorScheme.secondaryContainer 
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            !recording.mediaReady -> Icons.Filled.Videocam
                            isSound -> Icons.Filled.GraphicEq
                            else -> Icons.Filled.PlayCircle
                        },
                        contentDescription = null,
                        tint = if (recording.mediaReady) {
                            if (isSound) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (recording.mediaReady) formatDuration(recording.durationSeconds) else "Processing",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

private fun Recording.title(): String {
    return formatEventLabel(topLabel ?: "Recording")
}

private fun Recording.subtitle(): String {
    return formatTimestamp(startedAt)
}
