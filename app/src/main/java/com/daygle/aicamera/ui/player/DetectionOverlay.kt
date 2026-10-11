package com.daygle.aicamera.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

// The web overlay's colours: teal motion, cyan objects, amber "still".
private val MOTION_COLOR = Color(0xFF49E6A3)
private val OBJECT_COLOR = Color(0xFF47D6FF)
private val STILL_COLOR = Color(0xFFFBBF24)
private val LABEL_BACKGROUND = Color(0x94070B13)
private const val BOX_ALPHA = 0.78f

/**
 * Draws the clip's detection boxes over the video, following playback. Must
 * sit exactly over the player view (same bounds and transform), since boxes
 * are mapped into the letterboxed video area inside it.
 */
@Composable
fun DetectionOverlay(track: List<TrackFrame>, player: Player, modifier: Modifier = Modifier) {
    if (track.isEmpty()) return
    var time by remember { mutableDoubleStateOf(0.0) }
    var videoSize by remember { mutableStateOf(player.videoSize) }
    LaunchedEffect(player) {
        while (true) {
            withFrameNanos { }
            time = player.currentPosition / 1000.0
            videoSize = player.videoSize
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val detections = remember(track, time) { filterObjectPriority(sampleTrackAtTime(track, time)) }
    Canvas(modifier) {
        drawDetections(detections, videoSize, textMeasurer)
    }
}

private fun DrawScope.drawDetections(
    detections: List<TrackDetection>,
    videoSize: VideoSize,
    textMeasurer: TextMeasurer,
) {
    if (detections.isEmpty()) return
    // The player fits the video inside its bounds; find that letterboxed area.
    val srcW = (videoSize.width * videoSize.pixelWidthHeightRatio).takeIf { it > 0 } ?: size.width
    val srcH = videoSize.height.toFloat().takeIf { it > 0 } ?: size.height
    val scale = min(size.width / srcW, size.height / srcH)
    val renderW = srcW * scale
    val renderH = srcH * scale
    val offsetX = (size.width - renderW) / 2
    val offsetY = (size.height - renderH) / 2

    val stroke = 2.dp.toPx()
    val labelStyle = TextStyle(fontSize = 12.sp)
    val labelHeight = 20.dp.toPx()
    val pad = 6.dp.toPx()
    val gap = 4.dp.toPx()

    fun pill(text: String, color: Color, x: Float, y: Float): Float {
        val layout = textMeasurer.measure(text, labelStyle)
        val width = layout.size.width + pad * 2
        drawRect(LABEL_BACKGROUND, Offset(x, y), Size(width, labelHeight))
        drawText(layout, color, Offset(x + pad, y + (labelHeight - layout.size.height) / 2))
        return width
    }

    for (detection in detections) {
        val box = normalizeBox(detection.box, videoSize.width, videoSize.height) ?: continue
        val x = box.x.coerceIn(0.0, 1.0).toFloat()
        val y = box.y.coerceIn(0.0, 1.0).toFloat()
        val w = box.width.coerceIn(0.0, 1.0).toFloat()
        val h = box.height.coerceIn(0.0, 1.0).toFloat()
        val dx = offsetX + x * renderW
        val dy = offsetY + y * renderH
        val dw = w * renderW
        val dh = h * renderH
        if (dw < 2 || dh < 2) continue

        val color = if (detection.isMotion) MOTION_COLOR else OBJECT_COLOR
        drawRect(color.copy(alpha = BOX_ALPHA), Offset(dx, dy), Size(dw, dh), style = Stroke(stroke))

        val confidence = (detection.confidence * 100).roundToInt()
        val label = "${titleCase(detection.label.ifBlank { "object" })} $confidence%"
        val labelY = if (dy > labelHeight + gap) dy - labelHeight - gap else dy + gap
        var pillX = dx + pill(label, color, dx, labelY) + gap
        when (detection.motionState) {
            "moving" -> pillX += pill("Moving", MOTION_COLOR, pillX, labelY) + gap
            "still" -> pillX += pill("Still", STILL_COLOR, pillX, labelY) + gap
        }
        detection.validTrackId?.let { pill("#$it", MOTION_COLOR, pillX, labelY) }
    }
}

private fun titleCase(text: String): String =
    text.replace('_', ' ').split(' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
