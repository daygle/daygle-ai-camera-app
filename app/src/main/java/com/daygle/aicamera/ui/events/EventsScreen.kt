package com.daygle.aicamera.ui.events

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    modifier: Modifier = Modifier,
    onPlayRecording: (Int) -> Unit = {},
    refreshTrigger: Int = 0,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var snapshotEventId by remember { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    snapshotEventId?.let { id ->
        SnapshotViewerDialog(
            url = viewModel.snapshotUrl(id),
            onDismiss = { snapshotEventId = null },
        )
    }

    if (showFilterSheet) {
        EventsFilterSheet(
            state = (state as? EventsUiState.Ready)?.data?.filter ?: EventsFilter(),
            availableModes = (state as? EventsUiState.Ready)?.data?.availableModes ?: emptyList(),
            availableTriggerTypes = (state as? EventsUiState.Ready)?.data?.availableTriggerTypes ?: emptyList(),
            availableSources = (state as? EventsUiState.Ready)?.data?.availableSources ?: emptyList(),
            availableObjectLabels = (state as? EventsUiState.Ready)?.data?.availableObjectLabels ?: emptyList(),
            availableSoundLabels = (state as? EventsUiState.Ready)?.data?.availableSoundLabels ?: emptyList(),
            cameraMap = (state as? EventsUiState.Ready)?.data?.cameras?.associate { it.id to it.displayName } ?: emptyMap(),
            onDismiss = { showFilterSheet = false },
            viewModel = viewModel
        )
    }

    when (val s = state) {
        EventsUiState.Loading -> LoadingState(modifier)
        is EventsUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is EventsUiState.Ready -> {
            val data = s.data
            val activeFilterCount = data.filter.activeCount()

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
                if (data.events.size != data.filtered.size) {
                    Text(
                        "${data.filtered.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Event list
                PullToRefreshBox(
                    isRefreshing = data.refreshing,
                    onRefresh = viewModel::load,
                    modifier = Modifier.weight(1f),
                ) {
                    if (data.filtered.isEmpty()) {
                        EmptyState(
                            if (activeFilterCount > 0) "No events match your filters." else "No events recorded yet.",
                        )
                    } else {
                        val lazyListState = rememberLazyListState(
                            initialFirstVisibleItemIndex = viewModel.scrollIndex
                        )
                        LaunchedEffect(lazyListState) {
                            snapshotFlow { lazyListState.firstVisibleItemIndex }
                                .collect { index -> viewModel.saveScrollIndex(index) }
                        }
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(data.filtered, key = { it.id }) { event ->
                                EventRow(
                                    event,
                                    onPlayRecording = onPlayRecording,
                                    onOpenSnapshot = { snapshotEventId = it },
                                )
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
    availableModes: List<String>,
    availableTriggerTypes: List<String>,
    availableSources: List<String>,
    availableObjectLabels: List<String>,
    availableSoundLabels: List<String>,
    cameraMap: Map<String, String>,
    onDismiss: () -> Unit,
    viewModel: EventsViewModel
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
                .verticalScroll(rememberScrollState())
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
                    EventsSortOrder.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sortOrder == sort,
                            onClick = { viewModel.setSortOrder(sort) },
                            label = { Text(sort.label) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // High Priority Toggle
            FilterSection(title = "Alerts", icon = Icons.Filled.NotificationsActive) {
                FilterChip(
                    selected = state.alertedOnly,
                    onClick = { viewModel.setAlertedOnly(!state.alertedOnly) },
                    label = { Text("Only Show Alerts") },
                    shape = RoundedCornerShape(12.dp)
                )
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
                                selected = source in state.selectedCameras,
                                onClick = { viewModel.toggleCamera(source) },
                                label = { Text(cameraMap[source] ?: formatEventLabel(source)) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Labels
            if (availableObjectLabels.isNotEmpty()) {
                FilterSection(title = "Object Detections", icon = Icons.Filled.FilterList) {
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
                FilterSection(title = "Sound Detections", icon = Icons.Filled.GraphicEq) {
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


private fun getDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())

private fun dateRangeLabel(start: LocalDate?, end: LocalDate?): String {
    if (start == null && end == null) return "Anytime"
    val formatter = getDateFormatter()
    if (start != null && end != null) {
        return "${start.format(formatter)} - ${end.format(formatter)}"
    }
    return if (start != null) "From ${start.format(formatter)}" else "Until ${end!!.format(formatter)}"
}

@Composable
private fun EventRow(
    event: Event,
    onPlayRecording: (Int) -> Unit = {},
    onOpenSnapshot: (Int) -> Unit = {},
) {
    // Prefer the explicit recording link (recording : events = 1:many); fall
    // back to the embedded recordings list for older server payloads.
    val firstRecordingId = event.recordingId ?: event.recordings.firstOrNull()?.id
    val isSound = isSoundEvent(event)
    val detectionsToShow = if (event.detections.isEmpty() && event.source == "sound") {
        val confidence = (event.metadata["confidence"] as? JsonPrimitive)?.doubleOrNull
        val label = (event.metadata["label"] as? JsonPrimitive)?.contentOrNull
        if (confidence != null && label != null) listOf(Detection(label, confidence)) else emptyList()
    } else {
        event.detections
    }

    Card(
        onClick = {
            if (firstRecordingId != null) onPlayRecording(firstRecordingId)
        },
        enabled = firstRecordingId != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        )
    ) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        formatEventLabel(event.topLabel ?: "Event"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (event.alerted) AlertBadge()
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (isSound) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                        Text(
                            formatTimestamp(event.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (detectionsToShow.isNotEmpty()) {
                        Text(
                            detectionsToShow.joinToString(", ") {
                                "${formatEventLabel(it.label)} (${(it.confidence * 100).toInt()}%)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSound) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isSound) Icons.Filled.GraphicEq else Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = if (isSound) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (event.hasSnapshot) {
                        IconButton(
                            onClick = { onOpenSnapshot(event.id) },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = "View Snapshot",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (firstRecordingId != null) {
                        IconButton(
                            onClick = { onPlayRecording(firstRecordingId) },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        ) {
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = "Play Recording",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}


private fun isSoundEvent(event: Event): Boolean =
    event.source?.lowercase() == "sound" ||
        event.triggerType?.lowercase() == "sound" ||
        isSoundLabel(event.triggerLabel) ||
        event.detections.any { isSoundLabel(it.label) }

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

@Composable
private fun SnapshotViewerDialog(url: String?, onDismiss: () -> Unit) {
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
            } else {
                Text(
                    "Snapshot unavailable",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
