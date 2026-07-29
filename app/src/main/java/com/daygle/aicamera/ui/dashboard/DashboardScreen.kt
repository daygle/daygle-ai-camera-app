package com.daygle.aicamera.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.daygle.aicamera.data.model.CameraHealthSummary
import com.daygle.aicamera.data.model.StatusResponse
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.formatUptime

@Composable
fun DashboardScreen(
    onOpenCamera: (String) -> Unit,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    when (val s = state) {
        DashboardUiState.Loading -> LoadingState(modifier)
        is DashboardUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is DashboardUiState.Ready -> {
            if (s.cameras.isEmpty()) {
                EmptyState("No cameras are configured on this server yet.", modifier)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        SystemSummary(s.summary, s.status)
                    }
                    items(s.cameras, key = { it.camera.id }) { card ->
                        CameraTile(
                            card = card,
                            snapshotUrl = viewModel.snapshotUrl(card.camera.id),
                            onClick = { onOpenCamera(card.camera.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemSummary(summary: CameraHealthSummary, status: StatusResponse?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Stat(label = "Online", value = "${summary.online}/${summary.total}")
            val ai = status?.let {
                if (it.aiAvailable) it.aiBackend?.uppercase() ?: "ON" else "OFF"
            } ?: "-"
            Stat(label = "AI", value = ai)
            status?.let {
                if (it.uptimeSeconds > 0) Stat(label = "Uptime", value = formatUptime(it.uptimeSeconds))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CameraTile(card: CameraCard, snapshotUrl: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (card.online) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(snapshotUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = card.camera.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.VideocamOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(online = card.online)
                Spacer(Modifier.size(8.dp))
                Text(
                    card.camera.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (online) Color(0xFF3DDC97) else Color(0xFFFF6B6B)),
    )
}
