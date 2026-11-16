package com.daygle.aicamera.ui.events

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daygle.aicamera.data.model.AiVerdict
import com.daygle.aicamera.data.model.FaceFacets
import com.daygle.aicamera.ui.components.aiVerdictIcon
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.library.DateRangeFilterDialog
import com.daygle.aicamera.ui.library.FaceFilterChips
import com.daygle.aicamera.ui.library.FilterSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventsFilterSheet(
    state: EventsFilter,
    availableModes: List<String>,
    availableTriggerTypes: List<String>,
    availableSources: List<String>,
    availableObjectLabels: List<String>,
    availableSoundLabels: List<String>,
    availableAiTags: List<String> = emptyList(),
    faceFacets: FaceFacets? = null,
    cameraMap: Map<String, String>,
    onDismiss: () -> Unit,
    viewModel: EventsViewModel
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DateRangeFilterDialog(
            start = state.dateStart,
            end = state.dateEnd,
            onApply = viewModel::setDateRange,
            onDismiss = { showDatePicker = false },
        )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.alertedOnly,
                        onClick = { viewModel.setAlertedOnly(!state.alertedOnly) },
                        label = { Text("Fired Alerts") },
                        leadingIcon = { if (state.alertedOnly) Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // The AI alert verification verdict: check what the model stopped
            // (Filtered) or what went out without a verdict (Unverified).
            FilterSection(title = "AI Check", icon = Icons.Filled.Verified) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiVerdict.entries.forEach { verdict ->
                        val selected = state.aiCheck == verdict
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setAiCheck(if (selected) null else verdict) },
                            label = { Text(verdict.label) },
                            leadingIcon = { Icon(aiVerdictIcon(verdict), null, Modifier.size(18.dp)) },
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
