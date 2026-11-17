package com.daygle.aicamera.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daygle.aicamera.data.model.AiVerdict
import com.daygle.aicamera.data.model.AiVerification
import com.daygle.aicamera.data.model.FaceIdentities
import com.daygle.aicamera.data.model.FalsePositive
import com.daygle.aicamera.ui.formatEventLabel
import com.daygle.aicamera.ui.formatMotionFraction

/**
 * Wrapping row of the server's newer per-event insights, shared by the Events,
 * Recordings and Snapshots lists and the clip details: AI tags (objects the
 * vision model named), recognised faces, the AI alert verdict and the share
 * of the zone that changed on motion. Renders nothing when there is nothing
 * to show, so callers can drop it in unconditionally.
 */
@Composable
fun InsightChips(
    modifier: Modifier = Modifier,
    aiTags: List<String> = emptyList(),
    faces: FaceIdentities = FaceIdentities(),
    verification: AiVerification? = null,
    motionFraction: Double? = null,
    maxTags: Int = Int.MAX_VALUE,
    falsePositive: FalsePositive? = null,
    reclassified: Map<String, String> = emptyMap(),
) {
    if (aiTags.isEmpty() && faces.isEmpty && verification == null && motionFraction == null &&
        falsePositive == null && reclassified.isEmpty()
    ) return
    var showVerdict by rememberSaveable(verification) { mutableStateOf(false) }
    var showFalsePositive by rememberSaveable(falsePositive) { mutableStateOf(false) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        falsePositive?.let { mark ->
            // Outlined like the web UI's dashed badge; tapping shows who
            // marked it and, for the AI, the model's reasons.
            InsightChip(
                text = "False Positive · ${mark.source}",
                icon = Icons.Filled.DoNotDisturbOn,
                color = MaterialTheme.colorScheme.error,
                description = "${falsePositiveSummary(mark)} Tap for details.",
                dashed = true,
                onClick = { showFalsePositive = true },
            )
        }
        reclassified.forEach { (label, name) ->
            // A real object under the wrong label (a cat the detector called
            // a dog): the server corrected the label and never purges it.
            InsightChip(
                text = reclassifiedText(label),
                icon = Icons.Filled.SwapHoriz,
                color = MaterialTheme.colorScheme.tertiary,
                description = "The detector reported ${formatEventLabel(label)}; the AI model saw " +
                    "${formatEventLabel(name)} and reclassified it. Something real was there, so it is " +
                    "not a false positive.",
                dashed = true,
            )
        }
        verification?.let { record ->
            // Tapping the verdict shows the model's reasons: the web UI's
            // info bubble, which a phone has no hover to open.
            InsightChip(
                text = record.verdict.label,
                icon = aiVerdictIcon(record.verdict),
                color = aiVerdictColor(record.verdict),
                description = "${record.summary} Tap for the AI model's reasons.",
                dashed = record.verdict == AiVerdict.UNVERIFIED,
                onClick = { showVerdict = true },
            )
        }
        motionFraction?.let { fraction ->
            InsightChip(
                text = "Motion · ${formatMotionFraction(fraction)}",
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                color = MaterialTheme.colorScheme.secondary,
                description = "${formatMotionFraction(fraction)} of the zone's pixels changed",
            )
        }
        faces.people.forEach { person ->
            InsightChip(
                text = person.name,
                icon = Icons.Filled.Face,
                color = MaterialTheme.colorScheme.tertiary,
                description = "Recognised person: ${person.name}",
            )
        }
        if (faces.unknown > 0) {
            InsightChip(
                text = "Unknown face",
                icon = Icons.Outlined.Face,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                description = "Face not matched to an enrolled person",
            )
        }
        val shownTags = aiTags.take(maxTags)
        shownTags.forEach { tag ->
            InsightChip(
                text = formatEventLabel(tag),
                icon = Icons.Filled.AutoAwesome,
                color = MaterialTheme.colorScheme.tertiary,
                description = "AI tag: ${formatEventLabel(tag)} (named by the AI model, not a detection)",
                dashed = true,
            )
        }
        if (aiTags.size > shownTags.size) {
            InsightChip(
                text = "+${aiTags.size - shownTags.size}",
                icon = null,
                color = MaterialTheme.colorScheme.tertiary,
                description = "${aiTags.size - shownTags.size} more AI tags",
                dashed = true,
            )
        }
    }
    if (showVerdict && verification != null) {
        AiVerdictDialog(verification) { showVerdict = false }
    }
    if (showFalsePositive && falsePositive != null) {
        AlertDialog(
            onDismissRequest = { showFalsePositive = false },
            icon = { Icon(Icons.Filled.DoNotDisturbOn, contentDescription = null) },
            title = { Text("False Positive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(falsePositiveSummary(falsePositive), style = MaterialTheme.typography.bodyMedium)
                    falsePositive.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFalsePositive = false }) { Text("Close") } },
        )
    }
}

/** The chip for a reclassified label: "Reclassified from Dog". */
internal fun reclassifiedText(originalLabel: String): String = "Reclassified from ${formatEventLabel(originalLabel)}"

/** One sentence saying who marked a false positive. */
fun falsePositiveSummary(mark: FalsePositive): String = when (mark.by) {
    "ai" -> "The AI model judged that nothing it was asked about is really there."
    "mixed" -> "The AI model and a user marked these detections as false positives."
    else -> "Marked as a false positive by a user."
}

/**
 * Admin-only toggle that marks an event, snapshot or recording a false
 * positive, or (when [marked]) records it as a real detection so the
 * server's AI check leaves it alone. [busy] disables it while saving.
 */
@Composable
fun FalsePositiveButton(
    marked: Boolean,
    busy: Boolean,
    onSet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = { onSet(!marked) },
        enabled = !busy,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            if (marked) Icons.Filled.Check else Icons.Filled.DoNotDisturbOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            if (marked) "Not False Positive" else "Mark False Positive",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The icon for an AI verification verdict, shared by the chips and the Events filter. */
fun aiVerdictIcon(verdict: AiVerdict): ImageVector = when (verdict) {
    AiVerdict.CONFIRMED -> Icons.Filled.Verified
    AiVerdict.FILTERED -> Icons.Filled.Block
    AiVerdict.UNVERIFIED -> Icons.AutoMirrored.Outlined.HelpOutline
}

@Composable
private fun aiVerdictColor(verdict: AiVerdict): Color = when (verdict) {
    AiVerdict.CONFIRMED -> MaterialTheme.colorScheme.primary
    AiVerdict.FILTERED -> MaterialTheme.colorScheme.error
    AiVerdict.UNVERIFIED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The verdict's summary and the model's reason for each checked object. */
@Composable
private fun AiVerdictDialog(verification: AiVerification, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(aiVerdictIcon(verification.verdict), contentDescription = null) },
        title = { Text(verification.verdict.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(verification.summary, style = MaterialTheme.typography.bodyMedium)
                verification.details.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InsightChip(
    text: String,
    icon: ImageVector?,
    color: Color,
    description: String,
    dashed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f),
        // AI tags get an outline so they read as "named by the model", apart
        // from the solid detection and verdict chips (the web UI dashes them).
        // An Unverified verdict is outlined too: the model gave no ruling.
        border = if (dashed) BorderStroke(1.dp, color.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The AI model's one-sentence description of an event. Collapsed to
 * [collapsedLines] in lists; tapping expands it so long sentences do not
 * stretch every row.
 */
@Composable
fun AiDescriptionText(
    text: String?,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 2,
    expandable: Boolean = true,
) {
    if (text.isNullOrBlank()) return
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .animateContentSize()
            .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
            .semantics { contentDescription = "AI description: $text" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded || !expandable) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
