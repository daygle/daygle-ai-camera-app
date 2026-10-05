package com.daygle.aicamera.ui.snapshots

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.daygle.aicamera.R
import com.daygle.aicamera.data.model.Detection
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.aiDescription
import com.daygle.aicamera.data.model.aiVerdict
import com.daygle.aicamera.data.model.faceIdentities
import com.daygle.aicamera.data.model.FaceFacets
import com.daygle.aicamera.ui.components.AiDescriptionText
import com.daygle.aicamera.ui.library.FaceFilterChips
import com.daygle.aicamera.ui.library.LoadMoreEffect
import com.daygle.aicamera.ui.library.PagingFooter
import com.daygle.aicamera.ui.components.InsightChips
import com.daygle.aicamera.ui.formatDetectionSummary
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.components.ZoomableImage
import com.daygle.aicamera.ui.LocalUse24Hour
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.formatTimestamp
import com.daygle.aicamera.ui.ForceLandscape
import com.daygle.aicamera.ui.events.dateRangeLabel
import com.daygle.aicamera.ui.events.isMotionEvent
import com.daygle.aicamera.ui.events.isSoundEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotsScreen(
    modifier: Modifier = Modifier,
    viewModel: SnapshotsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openEventId by remember { mutableStateOf<Int?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }


    openEventId?.let { eventId ->
        val url = viewModel.snapshotUrl(eventId)
        SnapshotDialog(
            url = url,
            event = (state as? SnapshotsUiState.Ready)?.data?.snapshots?.firstOrNull { it.id == eventId },
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
            availableAiTags = (state as? SnapshotsUiState.Ready)?.data?.availableAiTags ?: emptyList(),
            faceFacets = (state as? SnapshotsUiState.Ready)?.data?.faceFacets,
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
                            placeholder = { Text("Search snapshots, AI tags, faces…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

                if (activeFilterCount > 0 && !data.refreshing) {
                    Text(
                        "${data.filtered.size}${if (data.hasMore) "+" else ""} matching snapshots",
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
                    if (data.filtered.isEmpty() && !data.hasMore && !data.loadingMore && data.loadMoreError == null) {
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
                        LoadMoreEffect(lazyListState, hasMore = data.hasMore, onLoadMore = viewModel::loadMore)
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(data.filtered, key = { it.id }) { event ->
                                val eventUrl = viewModel.thumbnailUrl(event.id)
                                SnapshotRow(
                                    event = event,
                                    url = eventUrl,
                                    onClick = { openEventId = event.id },
                                )
                            }
                            item(key = "paging-footer") {
                                PagingFooter(
                                    loadingMore = data.loadingMore,
                                    loadMoreError = data.loadMoreError,
                                    hasMore = data.hasMore,
                                    onLoadMore = viewModel::loadMore,
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
    availableAiTags: List<String>,
    faceFacets: FaceFacets?,
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

            if (faceFacets != null && (faceFacets.people.isNotEmpty() || faceFacets.unknown > 0)) {
                FilterSection(title = "Faces", icon = Icons.Filled.Face) {
                    FaceFilterChips(faces = faceFacets, selected = state.face, onSelect = viewModel::setFace)
                }
            }

            if (availableAiTags.isNotEmpty()) {
                FilterSection(title = "AI Tags", icon = Icons.Filled.AutoAwesome) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableAiTags.forEach { tag ->
                            FilterChip(
                                selected = tag in state.selectedLabels,
                                onClick = { viewModel.toggleLabel(tag) },
                                label = { Text(formatEventLabel(tag)) },
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
                            isMotion -> Icons.AutoMirrored.Filled.DirectionsRun
                            else -> Icons.Filled.Videocam
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatTimestamp(event.createdAt, LocalUse24Hour.current),
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
                        formatDetectionSummary(detectionsToShow),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val description = remember(event) { event.aiDescription() }
                val faces = remember(event) { event.faceIdentities() }
                InsightChips(aiTags = description?.tags.orEmpty(), faces = faces, maxTags = 3)
                AiDescriptionText(description?.text)
            }
            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotDialog(
    url: String?,
    event: Event?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (fullscreen && url != null) {
            FullscreenSnapshot(url = url, onExitFullscreen = { fullscreen = false })
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "Snapshot",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            if (url != null) {
                                IconButton(onClick = onDownload) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download")
                                }
                                IconButton(onClick = { fullscreen = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_fullscreen),
                                        contentDescription = "Full screen",
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                bottomBar = { if (event != null) SnapshotDetails(event) },
            ) { padding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (url != null) {
                            ZoomableImage(
                                model = url,
                                contentDescription = "Event snapshot",
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("Snapshot unavailable")
                        }
                    }
                }
            }
        }
    }
}

/**
 * What the snapshot shows beyond the image: detections, the AI model's
 * description and tags, recognised faces and the AI alert verdict.
 */
@Composable
internal fun SnapshotDetails(event: Event) {
    val description = remember(event) { event.aiDescription() }
    val faces = remember(event) { event.faceIdentities() }
    val verdict = remember(event) { event.aiVerdict() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${formatEventLabel(event.topLabel ?: "Snapshot")} · ${formatTimestamp(event.createdAt, LocalUse24Hour.current)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.detections.isNotEmpty()) {
                Text(
                    formatDetectionSummary(event.detections),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            InsightChips(aiTags = description?.tags.orEmpty(), faces = faces, verdict = verdict)
            AiDescriptionText(description?.text, expandable = false)
        }
    }
}

/**
 * Immersive full-screen snapshot. Rotates to landscape and hides the system bars
 * while active, filling the display with the image; pinch-to-zoom remains
 * available. Exits on back press or the on-screen control; system bars and the
 * previous orientation are restored on dispose.
 */
@Composable
private fun FullscreenSnapshot(
    url: String,
    onExitFullscreen: () -> Unit,
) {
    // Rotate to landscape for a wide, immersive view.
    ForceLandscape()

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(onBack = onExitFullscreen)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        ZoomableImage(
            model = url,
            contentDescription = "Event snapshot",
            modifier = Modifier.fillMaxSize(),
        )

        FilledTonalIconButton(
            onClick = onExitFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp)
                .size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fullscreen_exit),
                contentDescription = "Exit full screen",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
