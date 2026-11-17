package com.daygle.aicamera.ui.insights

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.daygle.aicamera.data.model.AiInsightsResponse
import com.daygle.aicamera.data.model.ConfidenceSuggestion
import com.daygle.aicamera.data.model.FrameArea
import com.daygle.aicamera.data.model.IgnoreAreaSuggestion
import com.daygle.aicamera.data.model.IgnoredArea
import com.daygle.aicamera.ui.components.SettingsSection
import com.daygle.aicamera.ui.formatEventLabel
import kotlin.math.roundToInt

/**
 * Admins only: the server's AI Insights, as on the web AI page. Raise a
 * minimum confidence the AI keeps disagreeing with, ignore (or stop
 * ignoring) a spot where it keeps saying an object is not there, and see AI
 * tags worth an alert. Every change asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(onBack: () -> Unit, viewModel: AiInsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<PendingChange?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(pending.title) },
            text = { Text(pending.text) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    pending.onConfirm()
                }) { Text(pending.action) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Insights", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val insights = state.insights
        when {
            state.loading -> Centered(padding) { CircularProgressIndicator() }
            insights == null -> Centered(padding) {
                Text(
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) { Text("Try Again") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                insightSections(
                    insights = insights,
                    busy = state.busy,
                    thumbnailUrl = viewModel::eventThumbnailUrl,
                    frameUrl = viewModel::cameraFrameUrl,
                    onApply = { item ->
                        confirm = PendingChange(
                            title = "Raise Minimum Confidence?",
                            text = "${formatEventLabel(item.label)} on ${item.cameraName.ifBlank { item.cameraId }} will need " +
                                "${percent(item.minConfidence)} to be detected. Real detections below that are missed too.",
                            action = "Raise",
                        ) { viewModel.apply(item) }
                    },
                    onIgnore = { item ->
                        confirm = PendingChange(
                            title = "Ignore This Spot?",
                            text = "${formatEventLabel(item.label)} will not be detected inside the outlined spot on " +
                                "${item.cameraName.ifBlank { item.cameraId }}. A real ${formatEventLabel(item.label).lowercase()} " +
                                "there would be missed too.",
                            action = "Ignore",
                        ) { viewModel.ignore(item) }
                    },
                    onStopIgnoring = { item ->
                        confirm = PendingChange(
                            title = "Stop Ignoring?",
                            text = "${formatEventLabel(item.label)} will be detected in this spot of ${item.zoneName} again.",
                            action = "Stop Ignoring",
                        ) { viewModel.stopIgnoring(item) }
                    },
                )
            }
        }
    }
}

private class PendingChange(val title: String, val text: String, val action: String, val onConfirm: () -> Unit)

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

private fun LazyListScope.insightSections(
    insights: AiInsightsResponse,
    busy: Boolean,
    thumbnailUrl: (Int) -> String?,
    frameUrl: (String) -> String?,
    onApply: (ConfidenceSuggestion) -> Unit,
    onIgnore: (IgnoreAreaSuggestion) -> Unit,
    onStopIgnoring: (IgnoredArea) -> Unit,
) {
    item(key = "tuning") {
        SettingsSection(title = "Minimum Confidence") {
            SectionBody {
                if (insights.tuning.isEmpty()) {
                    Note("No changes suggested. Suggestions appear once the AI has judged enough detections (last ${insights.tuningWindowDays} days).")
                }
                insights.tuning.forEach { item ->
                    val lost = if (item.confirmedLost > 0) {
                        "; ${item.confirmedLost} of ${item.confirmedTotal} confirmed ones would be dropped too"
                    } else {
                        ""
                    }
                    InsightRow(
                        title = "${item.cameraName.ifBlank { item.cameraId }}: ${formatEventLabel(item.label)}",
                        text = "Raise from ${percent(item.currentMinConfidence)} to ${percent(item.minConfidence)}. The AI rejected " +
                            "${item.rejectedBelow} of ${item.judgedBelow} detections below it$lost.",
                        action = "Apply",
                        enabled = !busy,
                        onAction = { onApply(item) },
                    )
                }
            }
        }
    }
    item(key = "areas") {
        SettingsSection(title = "Ignore Areas") {
            SectionBody {
                Note("Spots where the AI keeps saying an object is not there (a bush read as a dog). Check the picture first: a real object there would be missed too.")
                if (insights.ignoreAreas.isEmpty() && insights.ignoredAreas.isEmpty()) {
                    Note("No spots suggested yet.")
                }
                insights.ignoreAreas.forEach { item ->
                    AreaPreview(item.area, item.sampleEventId?.let(thumbnailUrl), frameUrl(item.cameraId))
                    InsightRow(
                        title = "${item.cameraName.ifBlank { item.cameraId }} (${item.zones.joinToString()}): ${formatEventLabel(item.label)}",
                        text = "The AI rejected ${item.rejected} detections in the outlined spot" +
                            (if (item.confirmed > 0) " and confirmed ${item.confirmed}" else "") + ".",
                        action = "Ignore",
                        enabled = !busy,
                        onAction = { onIgnore(item) },
                    )
                }
                insights.ignoredAreas.forEach { item ->
                    AreaPreview(item.area, null, frameUrl(item.cameraId))
                    InsightRow(
                        title = "${item.cameraName.ifBlank { item.cameraId }} (${item.zoneName}): ${formatEventLabel(item.label)} ignored",
                        text = if (item.inZone) {
                            "Not detected inside the outlined spot."
                        } else {
                            "This spot is now outside the ${item.zoneName} area, so it has no effect."
                        },
                        action = "Stop Ignoring",
                        enabled = !busy,
                        onAction = { onStopIgnoring(item) },
                    )
                }
            }
        }
    }
    item(key = "tags") {
        SettingsSection(title = "Frequent AI Tags") {
            SectionBody {
                if (insights.frequentTags.isEmpty()) {
                    Note("No frequent AI tags without an alert in the last ${insights.tagsWindowDays} days.")
                } else {
                    Note("Objects the AI keeps naming that no alert watches. Set up an AI tag alert on the server's Zones page.")
                }
                insights.frequentTags.forEach { camera ->
                    Text(camera.cameraName.ifBlank { camera.cameraId }, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        camera.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    "${formatEventLabel(tag.tag)} · ${tag.count}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionBody(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InsightRow(title: String, text: String, action: String, enabled: Boolean, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onAction, enabled = enabled) { Text(action) }
    }
}

/**
 * The spot outlined over the latest rejected snapshot, or over the camera's
 * current frame when there is none (or it was purged).
 */
@Composable
private fun AreaPreview(area: FrameArea, snapshotUrl: String?, frameUrl: String?) {
    var url by remember(snapshotUrl, frameUrl) { mutableStateOf(snapshotUrl ?: frameUrl) }
    val outline = MaterialTheme.colorScheme.error
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Camera view with the spot outlined",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
                onError = { if (url != frameUrl) url = frameUrl },
            )
        }
        Box(
            Modifier
                .offset(x = maxWidth * area.x.toFloat(), y = maxHeight * area.y.toFloat())
                .size(width = maxWidth * area.width.toFloat(), height = maxHeight * area.height.toFloat())
                .border(BorderStroke(2.dp, outline), RoundedCornerShape(4.dp))
                .background(outline.copy(alpha = 0.12f)),
        )
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
