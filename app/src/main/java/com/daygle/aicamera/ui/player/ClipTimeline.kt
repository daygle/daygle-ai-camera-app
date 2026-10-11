package com.daygle.aicamera.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.parseTimestamp
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The clip's pre-roll, event span and post-motion tail, mirroring the web
 * player's bar (`web/clip_timeline.js` on the server). Boundaries come from the
 * detection track (real detection times), not the configured pre/post values,
 * so the bar reflects what this clip actually captured. All times are seconds
 * into the clip.
 */
data class ClipTimelineModel(
    val duration: Double,
    val firstDetection: Double,
    val lastDetection: Double,
    val triggerAt: Double?,
    val extensions: List<ClipExtension>,
    val originalEnd: Double?,
) {
    /** One localized sample has no measurable span: drawn as a hairline. */
    val singleDetection: Boolean get() = lastDetection - firstDetection < 0.05
    val preRoll: Double get() = firstDetection
    val event: Double get() = lastDetection - firstDetection
    val tail: Double get() = duration - lastDetection
}

data class ClipExtension(val start: Double, val end: Double, val reason: String, val label: String?)

/**
 * Build the bar for [recording] at [duration] seconds, or null for clips with
 * no localized detections (continuous recordings get no segmentation).
 */
fun clipTimelineModel(recording: Recording, duration: Double): ClipTimelineModel? {
    if (duration <= 0) return null
    val localized = recording.track.orEmpty()
        .filter { it.detections.isNotEmpty() }
        .mapNotNull { sample -> sample.t?.takeIf { it.isFinite() && it >= 0 } }
    if (localized.isEmpty()) return null
    val first = localized.first().coerceIn(0.0, duration)
    val last = localized.last().coerceIn(first, duration)

    val startMs = parseTimestamp(recording.startedAt)?.toInstant()?.toEpochMilli()
    fun offset(iso: String?): Double? {
        if (startMs == null) return null
        val ms = parseTimestamp(iso)?.toInstant()?.toEpochMilli() ?: return null
        return (ms - startMs) / 1000.0
    }

    val trigger = offset(recording.event?.createdAt)?.takeIf { it in 0.0..duration }
    val ext = recording.extensions
    val runs = ext?.runs.orEmpty().mapNotNull { run ->
        val start = offset(run.start)?.takeIf { it in 0.0..duration } ?: return@mapNotNull null
        val end = (offset(run.end) ?: start).coerceIn(start, duration)
        ClipExtension(start, end, run.reason ?: "object", run.label)
    }
    // Only meaningful when the clip actually ran past it.
    val originalEnd = offset(ext?.originalEnd)?.takeIf { it > 0 && it < duration - 0.25 }

    return ClipTimelineModel(duration, first, last, trigger, runs, originalEnd)
}

/** Same short form as the web legend: `8.4s`, `41s`, `1:05`. */
fun formatClipSeconds(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0)
    if (s < 60) return if (s < 10) String.format(Locale.US, "%.1fs", s) else "${Math.round(s)}s"
    val minutes = (s / 60).toInt()
    val secs = Math.round(s % 60)
    return String.format(Locale.US, "%d:%02d", minutes, secs)
}

private class ClipTimelineColors(
    val track: Color,
    val pre: Color,
    val event: Color,
    val tail: Color,
    val trigger: Color,
    val marker: Color,
    val motion: Color,
    val still: Color,
    val playhead: Color,
)

// The web UI's --muted / --success / --accent / --warning / --text / --danger.
@Composable
private fun clipTimelineColors(): ClipTimelineColors {
    // Follow the app's own theme setting, which can differ from the system one.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val muted = if (dark) Color(0xFF91A1BA) else Color(0xFF66748E)
    val text = MaterialTheme.colorScheme.onSurface
    val warning = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
    return ClipTimelineColors(
        track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        pre = muted.copy(alpha = 0.4f),
        event = (if (dark) Color(0xFF49E6A3) else Color(0xFF059669)).copy(alpha = 0.85f),
        tail = (if (dark) Color(0xFF47D6FF) else Color(0xFF0284C7)).copy(alpha = 0.55f),
        trigger = warning,
        marker = text,
        motion = warning,
        still = muted,
        playhead = if (dark) Color(0xFFFF5C8A) else Color(0xFFDC2626),
    )
}

/**
 * The segmented bar plus its legend. Tap or drag on the bar to seek; the
 * playhead follows [player].
 */
@Composable
fun ClipTimeline(recording: Recording, player: Player, modifier: Modifier = Modifier) {
    var position by remember { mutableDoubleStateOf(0.0) }
    var playerDuration by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition / 1000.0
            val d = player.duration
            playerDuration = if (d != C.TIME_UNSET && d > 0) d / 1000.0 else 0.0
            delay(200)
        }
    }
    // The decoded file's length is authoritative; the stored one fills in until it loads.
    val duration = playerDuration.takeIf { it > 0 } ?: recording.durationSeconds
    val model = remember(recording, duration) { clipTimelineModel(recording, duration) } ?: return
    val colors = clipTimelineColors()

    fun seekTo(x: Float, width: Int) {
        if (width <= 0) return
        val fraction = (x / width).coerceIn(0f, 1f)
        player.seekTo((fraction * model.duration * 1000).toLong())
        position = fraction * model.duration
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .semantics {
                    contentDescription = "Clip timeline. Pre-roll ${formatClipSeconds(model.preRoll)}, " +
                        "event ${formatClipSeconds(model.event)}, tail ${formatClipSeconds(model.tail)}"
                }
                .pointerInput(model) {
                    detectTapGestures { seekTo(it.x, size.width) }
                }
                .pointerInput(model) {
                    detectHorizontalDragGestures { change, _ -> seekTo(change.position.x, size.width) }
                },
        ) {
            drawClipBar(model, position, colors)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendItem("Pre-roll ${formatClipSeconds(model.preRoll)}") { SwatchBox(colors.pre) }
            LegendItem(
                if (model.singleDetection) "Event: Single Detection" else "Event ${formatClipSeconds(model.event)}"
            ) { SwatchBox(colors.event) }
            LegendItem("Tail ${formatClipSeconds(model.tail)}") { SwatchBox(colors.tail) }
            model.triggerAt?.let { at ->
                LegendItem("Trigger ${formatClipSeconds(at)}") { SwatchLine(colors.trigger, dashed = true) }
            }
            if (model.extensions.isNotEmpty()) {
                LegendItem("Extended ×${model.extensions.size}") {
                    Canvas(Modifier.size(12.dp, 5.dp)) { drawRect(colors.marker.copy(alpha = 0.75f)) }
                }
            }
            model.originalEnd?.let { end ->
                LegendItem("Original end ${formatClipSeconds(end)}") {
                    SwatchLine(colors.marker, dashed = false, dotted = true)
                }
            }
        }
    }
}

private fun DrawScope.drawClipBar(model: ClipTimelineModel, position: Double, colors: ClipTimelineColors) {
    val w = size.width
    val h = size.height
    fun x(t: Double): Float = (t / model.duration).coerceIn(0.0, 1.0).toFloat() * w
    val radius = 6.dp.toPx()
    val clip = Path().apply {
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius)))
    }
    clipPath(clip) {
        drawRect(colors.track)
        fun band(start: Double, end: Double, color: Color) {
            if (end - start <= 0.05) return
            drawRect(color, Offset(x(start), 0f), Size(x(end) - x(start), h))
        }
        band(0.0, model.firstDetection, colors.pre)
        if (model.singleDetection) {
            val hair = 3.dp.toPx()
            drawRect(colors.event.copy(alpha = 1f), Offset(x(model.firstDetection) - hair / 3, 0f), Size(hair, h))
        } else {
            band(model.firstDetection, model.lastDetection, colors.event)
        }
        band(model.lastDetection, model.duration, colors.tail)

        // Extension runs: a strip along the bottom, notched where each starts.
        val stripH = 5.dp.toPx()
        val notch = 2.dp.toPx()
        for (run in model.extensions) {
            val color = when (run.reason) {
                "motion" -> colors.motion
                "still" -> colors.still
                else -> colors.marker
            }.copy(alpha = 0.75f)
            val left = x(run.start)
            val width = (x(run.end) - left).coerceAtLeast(3.dp.toPx())
            drawRect(color, Offset(left, h - stripH), Size(width, stripH))
            drawRect(colors.marker.copy(alpha = 0.75f), Offset(left, h - stripH), Size(notch, stripH))
        }

        val line = 2.dp.toPx()
        // Where the clip would have ended without the extensions.
        model.originalEnd?.let { end ->
            drawLine(
                colors.marker.copy(alpha = 0.7f), Offset(x(end), 0f), Offset(x(end), h), line,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(line, line)),
            )
        }
        // First detection: solid line with a dot on top.
        val fx = x(model.firstDetection)
        drawLine(colors.marker, Offset(fx, 0f), Offset(fx, h), line)
        drawCircle(colors.marker, 4.dp.toPx(), Offset(fx, 3.dp.toPx()))
        // Trigger: dashed line (what Pre-Event / Post-event are measured from).
        model.triggerAt?.let { at ->
            drawLine(
                colors.trigger, Offset(x(at), 0f), Offset(x(at), h), line,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
            )
        }
        drawRect(colors.playhead, Offset(x(position) - line / 2, 0f), Size(line, h))
    }
}

@Composable
private fun LegendItem(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) { swatch() }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwatchBox(color: Color) {
    Canvas(Modifier.size(12.dp)) { drawRoundRect(color, cornerRadius = CornerRadius(3.dp.toPx())) }
}

@Composable
private fun SwatchLine(color: Color, dashed: Boolean, dotted: Boolean = false) {
    Canvas(Modifier.width(2.dp).height(12.dp)) {
        val line = 2.dp.toPx()
        val effect = when {
            dotted -> PathEffect.dashPathEffect(floatArrayOf(line, line))
            dashed -> PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
            else -> null
        }
        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), line, pathEffect = effect)
    }
}
