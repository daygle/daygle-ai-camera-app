package com.daygle.aicamera.ui.events

import com.daygle.aicamera.data.model.reclassified
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.FollowTheSigns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daygle.aicamera.data.model.Detection
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.aiDescription
import com.daygle.aicamera.data.model.aiVerification
import com.daygle.aicamera.data.model.faceIdentities
import com.daygle.aicamera.ui.LocalUse24Hour
import com.daygle.aicamera.ui.components.AiDescriptionText
import com.daygle.aicamera.ui.components.InsightChips
import com.daygle.aicamera.ui.formatDetectionSummary
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.formatTimestamp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Composable
internal fun EventRow(
    event: Event,
    onPlayRecording: (Int) -> Unit = {},
    onOpenSnapshot: (Int) -> Unit = {},
    canMarkFalsePositive: Boolean = false,
    onSetFalsePositive: (Boolean) -> Unit = {},
) {
    // A clip the server is still writing cannot play yet: hold the Play
    // action (as the web Events page does) until a refresh reports it ready.
    val preparing = event.recordingPreparing
    val firstRecordingId = event.playableRecordingId?.takeUnless { preparing }
    val isSound = isSoundEvent(event)
    val isMotion = isMotionEvent(event)
    val isBehaviour = event.isBehaviourEvent()

    val detectionsToShow = if (event.detections.isEmpty() && event.source == "sound") {
        listOfNotNull(
            (event.metadata["confidence"] as? JsonPrimitive)?.doubleOrNull?.let { confidence ->
                (event.metadata["label"] as? JsonPrimitive)?.contentOrNull?.let { Detection(it, confidence) }
            }
        )
    } else {
        event.detections
    }

    val description = remember(event) { event.aiDescription() }
    val faces = remember(event) { event.faceIdentities() }
    val verdict = remember(event) { event.aiVerification() }

    // Admins long-press a row to mark it a false positive (or a real
    // detection). Sound events have no detector box to judge.
    val canMark = canMarkFalsePositive && !isSound
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    enabled = firstRecordingId != null || canMark,
                    onClick = { if (firstRecordingId != null) onPlayRecording(firstRecordingId) },
                    onLongClick = if (canMark) {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    } else {
                        null
                    },
                    onLongClickLabel = if (canMark) "False positive options" else null,
                ),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
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
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = when {
                                    isSound -> Icons.Filled.GraphicEq
                                    isMotion -> Icons.AutoMirrored.Filled.DirectionsRun
                                    isBehaviour -> Icons.AutoMirrored.Filled.FollowTheSigns
                                    else -> Icons.Filled.Videocam
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            )
                            Text(
                                formatTimestamp(event.createdAt, LocalUse24Hour.current),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (detectionsToShow.isNotEmpty()) {
                            Text(
                                formatDetectionSummary(detectionsToShow),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        event.behaviourContext()?.let { context ->
                            Text(
                                context,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        InsightChips(
                            aiTags = description?.tags.orEmpty(),
                            faces = faces,
                            verification = verdict,
                            maxTags = 4,
                            falsePositive = event.falsePositive,
                            reclassified = event.reclassified(),
                            shownDetections = detectionsToShow,
                        )
                        AiDescriptionText(description?.text)
                    }
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSound -> MaterialTheme.colorScheme.tertiaryContainer
                                    isMotion -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when {
                                isSound -> Icons.Filled.GraphicEq
                                isMotion -> Icons.AutoMirrored.Filled.DirectionsRun
                                isBehaviour -> Icons.AutoMirrored.Filled.FollowTheSigns
                                else -> Icons.Filled.NotificationsActive
                            },
                            contentDescription = null,
                            tint = when {
                                isSound -> MaterialTheme.colorScheme.onTertiaryContainer
                                isMotion -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                trailingContent = {
                    Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Older servers omit `has_snapshot` but still store the
                        // image (see CameraRepository.snapshotsPage).
                        if (event.hasSnapshot || !event.snapshotPath.isNullOrBlank()) {
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
                        if (preparing) {
                            PreparingBadge()
                        } else if (firstRecordingId != null) {
                            IconButton(
                                onClick = { onPlayRecording(firstRecordingId) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
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
        if (canMark) {
            val marked = event.falsePositive != null
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (marked) "Not a False Positive" else "Mark as False Positive") },
                    leadingIcon = {
                        Icon(if (marked) Icons.Filled.Check else Icons.Filled.DoNotDisturbOn, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onSetFalsePositive(!marked)
                    },
                )
            }
        }
    }
}

/** Stands in for the Play action while the event's clip is still being written. */
@Composable
private fun PreparingBadge() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.HourglassTop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Preparing…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AlertBadge() {
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
