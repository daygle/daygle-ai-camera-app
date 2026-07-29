package com.daygle.aicamera.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.util.Locale

@Composable
fun RecordingsScreen(
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: RecordingsViewModel = viewModel(factory = RecordingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    when (val s = state) {
        RecordingsUiState.Loading -> LoadingState(modifier)
        is RecordingsUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is RecordingsUiState.Ready -> {
            if (s.recordings.isEmpty()) {
                EmptyState("No recordings on the server yet.", modifier)
            } else {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.recordings, key = { it.id }) { recording ->
                        RecordingRow(recording, onPlay = { onPlay(recording.id) })
                    }
                }
            }
        }
    }
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
                    formatTimestamp(recording.startedAt),
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
