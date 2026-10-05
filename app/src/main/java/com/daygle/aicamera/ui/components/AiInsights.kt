package com.daygle.aicamera.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.daygle.aicamera.data.model.FaceIdentities
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
    verdict: AiVerdict? = null,
    motionFraction: Double? = null,
    maxTags: Int = Int.MAX_VALUE,
) {
    if (aiTags.isEmpty() && faces.isEmpty && verdict == null && motionFraction == null) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (verdict) {
            AiVerdict.CONFIRMED -> InsightChip(
                text = "AI Verified",
                icon = Icons.Filled.Verified,
                color = MaterialTheme.colorScheme.primary,
                description = "The AI model confirmed this alert",
            )
            AiVerdict.FILTERED -> InsightChip(
                text = "AI Filtered",
                icon = Icons.Filled.Block,
                color = MaterialTheme.colorScheme.error,
                description = "Notification not sent: the AI model judged this a false alarm",
            )
            null -> Unit
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
}

@Composable
private fun InsightChip(
    text: String,
    icon: ImageVector?,
    color: Color,
    description: String,
    dashed: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f),
        // AI tags get an outline so they read as "named by the model", apart
        // from the solid detection and verdict chips (the web UI dashes them).
        border = if (dashed) BorderStroke(1.dp, color.copy(alpha = 0.45f)) else null,
        modifier = Modifier.semantics { contentDescription = description },
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
