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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.formatTimestamp
import java.util.Locale

@Composable
fun EventsScreen(
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: EventsViewModel = viewModel(factory = EventsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    when (val s = state) {
        EventsUiState.Loading -> LoadingState(modifier)
        is EventsUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is EventsUiState.Ready -> {
            val data = s.data
            Column(modifier) {
                // Search bar
                OutlinedTextField(
                    value = data.filter.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search labels, sources...") },
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

                // Source chips
                if (data.availableSources.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        data.availableSources.forEach { source ->
                            FilterChip(
                                selected = source in data.filter.selectedSources,
                                onClick = { viewModel.toggleSource(source) },
                                label = {
                                    Text(source.replaceFirstChar { it.titlecase(Locale.getDefault()) })
                                },
                            )
                        }
                    }
                }

                // Label chips
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

                // Alerted-only toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !data.filter.alertedOnly,
                        onClick = { viewModel.setAlertedOnly(false) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = data.filter.alertedOnly,
                        onClick = { viewModel.setAlertedOnly(true) },
                        label = { Text("Alerts only") },
                    )
                }

                // Active filter count & clear
                val hasActiveFilters = data.filter.query.isNotBlank() ||
                    data.filter.selectedSources.isNotEmpty() ||
                    data.filter.selectedLabels.isNotEmpty() ||
                    data.filter.alertedOnly

                if (hasActiveFilters) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${data.filtered.size} of ${data.events.size} events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = viewModel::clearFilters) {
                            Text("Clear filters")
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Event list
                if (data.filtered.isEmpty()) {
                    EmptyState(
                        if (hasActiveFilters) "No events match your filters." else "No events recorded yet.",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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

@Composable
private fun EventRow(event: Event) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (event.source == "sound") Icons.Filled.Sensors else Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.topLabel?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                        ?: event.source?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                        ?: "Event",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        formatTimestamp(event.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    if (event.source != null) {
                        Text(
                            event.source.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                    }
                }
                if (event.detections.isNotEmpty()) {
                    Text(
                        event.detections.joinToString(", ") {
                            "${it.label} (${(it.confidence * 100).toInt()}%)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                    )
                }
            }
            if (event.alerted) {
                AlertBadge()
            }
        }
    }
}

@Composable
private fun AlertBadge() {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Alert",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
