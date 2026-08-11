package com.daygle.aicamera.ui.snapshots

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.daygle.aicamera.data.model.Detection
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.components.ZoomableImage
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.formatTimestamp
import com.daygle.aicamera.ui.isMotionLabel
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
fun SnapshotsScreen(
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: SnapshotsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openEventId by remember { mutableStateOf<Int?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

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

    if (showFilterSheet) {
        SnapshotsFilterSheet(
            state = (state as? SnapshotsUiState.Ready)?.data?.filter ?: SnapshotsFilter(),
            availableModes = (state as? SnapshotsUiState.Ready)?.data?.availableModes ?: emptyList(),
            availableSources = (state as? SnapshotsUiState.Ready)?.data?.availableSources ?: emptyList(),
            availableTriggerTypes = (state as? SnapshotsUiState.Ready)?.data?.availableTriggerTypes ?: emptyList(),
            availableObjectLabels = (state as? SnapshotsUiState.Ready)?.data?.availableObjectLabels ?: emptyList(),
            availableSoundLabels = (state as? SnapshotsUiState.Ready)?.data?.availableSoundLabels ?: emptyList(),
            cameraMap = (state as? SnapshotsUiState.Ready)?.data?.cameras?.associate { it.id to it.displayName } ?: emptyMap(),
            onDismiss = { showFilterSheet = false },
            viewModel = viewModel
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
                    AssistChip(
                        onClick = { showFilterSheet = true },
                        label = { Text(data.filter.sortOrder.label) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    data.filter.selectedModes.forEach { mode ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.toggleMode(mode) },
                            label = { Text(mode) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

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

                if (data.snapshots.size != data.filtered.size) {
                    Text(
                        "${data.filtered.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                PullToRefreshBox(
                    isRefreshing = data.refreshing,
                    onRefresh = viewModel::load,
                    modifier = Modifier.weight(1f),
                ) {
                    if (data.filtered.isEmpty()) {
                        EmptyState(
                            if (activeFilterCount > 0) "No snapshots match your filters." else "No snapshots on the server yet.",
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
                                val eventUrl = viewModel.snapshotUrl(event.id)
                                SnapshotRow(
                                    event = event,
                                    url = eventUrl,
                                    onClick = { openEventId = event.id },
                                    onDownload = {
                                        eventUrl?.let {
                                            val fileName = "snapshot-${event.id}.jpg"
                                            viewModel.download(it, fileName)
                                        }
                                    },
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
private fun SnapshotsFilterSheet(
    state: SnapshotsFilter,
    availableModes: List<String>,
    availableSources: List<String>,
    availableTriggerTypes: List<String>,
    availableObjectLabels: List<String>,
    availableSoundLabels: List<String>,
    cameraMap: Map<String, String>,
    onDismiss: () -> Unit,
    viewModel: SnapshotsViewModel
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

            FilterSection(title = "Sort By", icon = Icons.AutoMirrored.Filled.Sort) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SnapshotsSortOrder.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sortOrder == sort,
                            onClick = { viewModel.setSortOrder(sort) },
                            label = { Text(sort.label) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

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

            FilterSection(title = "Time Period", icon = Icons.Filled.CalendarMonth) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(dateRangeLabel(state.dateStart, state.dateEnd)) },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(12.dp)
                )
            }

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
private fun SnapshotRow(
    event: Event,
    url: String?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
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
                    when {
                        isSoundEvent(event) -> EventTypeBadge(mode = "Sound")
                        isMotionEvent(event) -> EventTypeBadge(mode = "Motion")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val isSound = isSoundEvent(event)
                    val isMotion = isMotionEvent(event)
                    Icon(
                        when {
                            isSound -> Icons.Filled.GraphicEq
                            isMotion -> Icons.Filled.DirectionsRun
                            else -> Icons.Filled.Videocam
                        },
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
            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Download button
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // View snapshot button
                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = "View Snapshot",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EventTypeBadge(mode: String) {
    val container = when (mode) {
        "Sound" -> MaterialTheme.colorScheme.tertiaryContainer
        "Motion" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val onContainer = when (mode) {
        "Sound" -> MaterialTheme.colorScheme.onTertiaryContainer
        "Motion" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            mode,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = onContainer,
        )
    }
}


private fun isMotionEvent(event: Event): Boolean =
    event.source?.lowercase() == "motion" ||
        event.triggerType?.lowercase() == "motion" ||
        isMotionLabel(event.triggerLabel) ||
        event.detections.any { isMotionLabel(it.label) }

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
                ZoomableImage(
                    model = url,
                    contentDescription = "Event snapshot",
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
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
