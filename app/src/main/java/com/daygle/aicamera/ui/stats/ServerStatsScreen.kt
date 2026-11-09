package com.daygle.aicamera.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.data.model.ActivityStats
import com.daygle.aicamera.data.model.CameraHealthResponse
import com.daygle.aicamera.data.model.StorageStatus
import com.daygle.aicamera.data.model.VideoDecodeStatus
import com.daygle.aicamera.ui.LifecycleResumeEffect
import com.daygle.aicamera.ui.LocalUse24Hour
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.components.SettingsSection
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.timeFormatter

/**
 * Live health of the Daygle server: CPU, load, RAM and GPU (as on the web
 * System page), camera connectivity, video decoding and detection activity.
 * Host stats need an admin account; camera health is shown to everyone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatsScreen(
    onBack: () -> Unit,
    viewModel: ServerStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Poll only while the screen is visible.
    LifecycleResumeEffect(onPause = viewModel::stop, onResume = viewModel::start)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Stats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val s = state
        when {
            s.loading -> LoadingState(Modifier.padding(padding))
            !s.notAdmin && s.resources == null && s.error != null ->
                ErrorState(s.error, modifier = Modifier.padding(padding), onRetry = viewModel::refresh)
            else -> PullToRefreshBox(
                isRefreshing = s.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                StatsContent(s, onRange = viewModel::setRange)
            }
        }
    }
}

@Composable
private fun StatsContent(state: ServerStatsUiState, onRange: (ActivityRange) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        val resources = state.resources
        if (state.notAdmin) {
            Text(
                "Only administrators can see the server's processor, memory and GPU.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (resources != null) {
            val tiles = listOfNotNull(
                cpuTile(resources),
                loadTile(resources),
                ramTile(resources),
                gpuTile(resources.gpu),
                vramTile(resources.gpu),
            )
            TileGrid(tiles)
        }

        if (!state.notAdmin) state.storage?.let { StorageCard(it) }

        state.health?.let { CamerasCard(it, names = resources?.videoDecode?.cameras.orEmpty().associate { c -> c.cameraId.orEmpty() to c.name.orEmpty() }) }

        if (!state.notAdmin) {
            ActivityCard(state.activity, state.range, onRange)
            resources?.videoDecode?.let { DecodeCard(it) }
        }

        state.updatedAt?.let { time ->
            Text(
                "Updated ${time.format(timeFormatter(LocalUse24Hour.current))} · refreshes every 5 s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Two tiles per row; an odd last tile spans the row. */
@Composable
private fun TileGrid(tiles: List<StatTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            // Tiles in a row share the taller one's height.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> StatTileCard(tile, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun StatTileCard(tile: StatTile, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = levelColor(tile.level)
    val container = when (tile.level) {
        StatLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
        StatLevel.WARN -> if (dark) Color(0xFF3D3010) else Color(0xFFFFF1CC)
        StatLevel.NORMAL -> MaterialTheme.colorScheme.surfaceBright
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                tile.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(tile.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (tile.level == StatLevel.NORMAL) MaterialTheme.colorScheme.onSurface else accent)
            tile.fraction?.let {
                LinearProgressIndicator(
                    progress = { it },
                    color = accent,
                    trackColor = accent.copy(alpha = 0.15f),
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
            Text(
                tile.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tint for a [StatLevel]: amber for a warning, red at the limit, primary otherwise. */
@Composable
private fun levelColor(level: StatLevel): Color = when (level) {
    StatLevel.CRITICAL -> MaterialTheme.colorScheme.error
    StatLevel.WARN -> if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFF2C14E) else Color(0xFF9A6700)
    StatLevel.NORMAL -> MaterialTheme.colorScheme.primary
}

@Composable
private fun StorageCard(storage: StorageStatus) {
    SettingsSection(title = "Storage", icon = Icons.Filled.Storage) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(storageSummary(storage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            storage.volumes.forEach { volume ->
                val accent = levelColor(storageLevel(volume.percent))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(volumeLabel(volume.holds), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        volume.percent?.let { Text("${it.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent) }
                    }
                    LinearProgressIndicator(
                        progress = { ((volume.percent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
                        color = accent,
                        trackColor = accent.copy(alpha = 0.15f),
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text(
                        volumeDetail(volume.usedBytes, volume.totalBytes, volume.freeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val usage = listOfNotNull(
                storage.usage.recordingsBytes?.let { "Recordings" to it },
                storage.usage.snapshotsBytes?.let { "Snapshots" to it },
                storage.usage.eventsBytes?.let { "Events" to it },
                storage.usage.databaseBytes?.let { "Database" to it },
            )
            usage.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, bytes) -> UsageBox(label, formatBytes(bytes), Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun UsageBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CamerasCard(health: CameraHealthResponse, names: Map<String, String>) {
    val online = health.cameras.count { it.value.online }
    val offline = health.cameras.filterValues { !it.online }.keys.map { names[it]?.takeIf(String::isNotBlank) ?: it }.sorted()
    SettingsSection(title = "Cameras", icon = Icons.Filled.Videocam) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CountDot(online, "online", MaterialTheme.colorScheme.primary)
                CountDot(offline.size, "offline", if (offline.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
            }
            if (offline.isNotEmpty()) {
                Text(
                    "Offline: " + offline.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CountDot(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCard(activity: ActivityStats?, range: ActivityRange, onRange: (ActivityRange) -> Unit) {
    SettingsSection(title = "Activity", icon = Icons.Filled.BarChart) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ActivityRange.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == range,
                        onClick = { onRange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, ActivityRange.entries.size),
                    ) { Text(option.label) }
                }
            }
            if (activity == null) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(Modifier.width(120.dp))
                }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Count("Events", activity.totalEvents, Modifier.weight(1f))
                Count("Alerts", activity.totalAlerts, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Count("Objects", activity.objectEvents, Modifier.weight(1f))
                Count("Sounds", activity.soundEvents, Modifier.weight(1f))
                Count("Motion", activity.motionEvents, Modifier.weight(1f))
            }
            val top = activity.objects.filter { it.label.isNotBlank() }.take(TOP_LABELS)
            if (top.isNotEmpty()) {
                Text("Top detections", style = MaterialTheme.typography.titleSmall)
                val max = top.maxOf { it.count }.coerceAtLeast(1)
                top.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(formatEventLabel(entry.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${entry.count}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { entry.count.toFloat() / max },
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                }
            } else {
                Text(
                    if (range == ActivityRange.TODAY) "Nothing detected yet today." else "Nothing detected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Count(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        // One step up from the card, so the box shows in both themes.
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DecodeCard(status: VideoDecodeStatus) {
    SettingsSection(title = "Video Decoding", icon = Icons.Filled.Movie) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(decodeSummary(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status.cameras.forEach { camera ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        camera.name?.takeIf { it.isNotBlank() } ?: camera.cameraId.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val gpu = camera.decode == "gpu"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (gpu) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            (if (gpu) "GPU (NVDEC)" else "CPU") + if (camera.gpuFallback) " · GPU failed" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (gpu) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val TOP_LABELS = 5
