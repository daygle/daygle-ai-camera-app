package com.daygle.aicamera.ui.recordings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.formatDuration
import com.daygle.aicamera.ui.formatTimestamp
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
    viewModel: RecordingsViewModel = viewModel(factory = RecordingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    // Date range picker dialog
    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = viewModel.state.value
                .let { s -> (s as? RecordingsUiState.Ready)?.data?.filter?.dateStart }
                ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = viewModel.state.value
                .let { s -> (s as? RecordingsUiState.Ready)?.data?.filter?.dateEnd }
                ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
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

    when (val s = state) {
        RecordingsUiState.Loading -> LoadingState(modifier)
        is RecordingsUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is RecordingsUiState.Ready -> {
            val data = s.data
            Column(modifier) {
                // Search bar
                OutlinedTextField(
                    value = data.filter.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search labels, triggers...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (data.filter.query.isNotBlank()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                // Date range picker row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = {
                            Text(
                                dateRangeLabel(data.filter.dateStart, data.filter.dateEnd),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = "Pick date range",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    if (data.filter.dateStart != null || data.filter.dateEnd != null) {
                        TextButton(onClick = { viewModel.setDateRange(null, null) }) {
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Sort order row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortOrder.entries.forEach { sort ->
                        FilterChip(
                            selected = data.filter.sortOrder == sort,
                            onClick = { viewModel.setSortOrder(sort) },
                            label = { Text(sort.label) },
                        )
                    }
                }

                // Label / trigger chips
                if (data.availableLabels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        data.availableLabels.forEach { label ->
                            FilterChip(
                                selected = label in data.filter.selectedLabels,
                                onClick = { viewModel.toggleLabel(label) },
                                label = {
                                    Text(label.replaceFirstChar { it.titlecase(Locale.getDefault()) })
                                },
                            )
                        }
                    }
                }

                // Active filter count & clear
                val hasActiveFilters = data.filter.query.isNotBlank() ||
                    data.filter.dateStart != null ||
                    data.filter.dateEnd != null ||
                    data.filter.selectedLabels.isNotEmpty()

                if (hasActiveFilters) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${data.filtered.size} of ${data.recordings.size} recordings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = viewModel::clearFilters) {
                            Text("Clear filters")
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Recording list
                if (data.filtered.isEmpty()) {
                    EmptyState(
                        if (hasActiveFilters) "No recordings match your filters." else "No recordings on the server yet.",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())

private fun dateRangeLabel(start: LocalDate?, end: LocalDate?): String {
    if (start == null && end == null) return "Pick date range"
    if (start != null && end != null) {
        return "${start.format(dateFormatter)} - ${end.format(dateFormatter)}"
    }
    return if (start != null) "From ${start.format(dateFormatter)}" else "Until ${end!!.format(dateFormatter)}"
}

@Composable
private fun RecordingRow(recording: Recording, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        enabled = recording.mediaReady,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (recording.mediaReady) Icons.Filled.PlayCircle else Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = if (recording.mediaReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recording.title(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    recording.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (recording.mediaReady) formatDuration(recording.durationSeconds) else "processing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

private fun Recording.title(): String {
    val label = labels.firstOrNull() ?: triggerLabel ?: triggerType ?: "Recording"
    return label.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun Recording.subtitle(): String {
    val parts = mutableListOf<String>()
    formatTimestamp(startedAt).let { if (it != "-") parts.add(it) }
    if (labels.isNotEmpty()) parts.add(labels.joinToString(", ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } })
    return parts.joinToString("  ·  ")
}
