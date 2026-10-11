package com.daygle.aicamera.ui.player

import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.SystemSettings
import com.daygle.aicamera.data.model.TrackSample
import com.daygle.aicamera.ui.isSoundDetection
import com.daygle.aicamera.ui.parseTimestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.math.max
import kotlin.math.min

/**
 * Detection-track playback for the object overlay: a port of the web player's
 * `web/overlay.js` (server repo), so boxes move the same way in both clients.
 * Times are seconds into the clip; boxes are normalized (0-1) to the frame.
 */

@Serializable
data class TrackBox(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
) {
    val centerX: Double get() = x + width / 2
    val centerY: Double get() = y + height / 2
}

@Serializable
data class TrackDetection(
    val label: String = "",
    val confidence: Double = 0.0,
    val box: TrackBox? = null,
    @SerialName("motion_event") val motionEvent: Boolean = false,
    // "moving" / "still", classified server-side; null on legacy samples.
    @SerialName("motion_state") val motionState: String? = null,
    // Stable tracker identity; null or <= 0 when the sample has none.
    @SerialName("track_id") val trackId: Int? = null,
    @SerialName("zone_name") val zoneName: String? = null,
) {
    val normalizedLabel: String get() = label.trim().lowercase()
    val isMotion: Boolean get() = motionEvent || normalizedLabel == "motion"
    val validTrackId: Int? get() = trackId?.takeIf { it > 0 }
}

data class TrackFrame(val t: Double, val detections: List<TrackDetection>)

/**
 * Decode the recording's raw track into frames sorted by time. Each detection
 * is decoded on its own, so one malformed entry drops only itself.
 */
fun parseDetectionTrack(samples: List<TrackSample>?): List<TrackFrame> =
    samples.orEmpty().mapNotNull { sample ->
        val t = sample.t?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
        TrackFrame(t, sample.detections.mapNotNull(::decodeDetection))
    }.sortedBy { it.t }

private fun decodeDetection(element: JsonElement): TrackDetection? =
    runCatching { SessionManager.json.decodeFromJsonElement(TrackDetection.serializer(), element) }.getOrNull()

/**
 * The box in normalized coordinates. The detector emits 0-1 values; anything
 * clearly in pixel space is divided by the frame size (null if unknown).
 */
fun normalizeBox(box: TrackBox?, frameWidth: Int, frameHeight: Int): TrackBox? {
    if (box == null) return null
    val values = listOf(box.x, box.y, box.width, box.height)
    if (values.any { !it.isFinite() } || box.width <= 0 || box.height <= 0) return null
    if (values.all { it <= 1 }) return box
    if (frameWidth <= 0 || frameHeight <= 0) return null
    return TrackBox(
        (box.x / frameWidth).coerceIn(0.0, 1.0),
        (box.y / frameHeight).coerceIn(0.0, 1.0),
        (box.width / frameWidth).coerceIn(0.0, 1.0),
        (box.height / frameHeight).coerceIn(0.0, 1.0),
    )
}

private val GENERIC_LABELS = setOf("", "motion", "alert", "human", "object", "none", "off", "continuous")

/**
 * A concrete object explains an overlapping generic motion box, so drop motion
 * boxes that overlap an object box by [minMotionOverlap]; unrelated motion stays.
 */
fun filterObjectPriority(detections: List<TrackDetection>, minMotionOverlap: Double = 0.15): List<TrackDetection> {
    val objectBoxes = detections
        .filter { it.box != null && it.normalizedLabel !in GENERIC_LABELS && !it.motionEvent }
        .mapNotNull { it.box }
    if (objectBoxes.isEmpty()) return detections
    return detections.filter { det ->
        !det.isMotion || objectBoxes.none { overlapRatio(det.box, it) >= minMotionOverlap }
    }
}

/** Share of [outer]'s area covered by [inner]. */
private fun overlapRatio(outer: TrackBox?, inner: TrackBox?): Double {
    if (outer == null || inner == null) return 0.0
    val outerArea = max(0.0, outer.width) * max(0.0, outer.height)
    if (outerArea <= 0 || inner.width <= 0 || inner.height <= 0) return 0.0
    return intersection(outer, inner) / outerArea
}

private fun intersection(a: TrackBox, b: TrackBox): Double {
    val ix = max(0.0, min(a.x + a.width, b.x + b.width) - max(a.x, b.x))
    val iy = max(0.0, min(a.y + a.height, b.y + b.height) - max(a.y, b.y))
    return ix * iy
}

private fun iou(a: TrackBox, b: TrackBox): Double {
    val inter = intersection(a, b)
    if (inter <= 0) return 0.0
    val union = a.width * a.height + b.width * b.height - inter
    return if (union > 0) inter / union else 0.0
}

// Same-label boxes whose centres jump more than a quarter frame between
// samples are a re-acquired object, not one moving: snap rather than glide.
private const val MAX_MATCH_CENTER_DISTANCE_SQ = 0.25 * 0.25

/**
 * The detection in [candidates] that is the same object as [target]: same
 * label, the same tracker id when that id is present, then the best overlap,
 * else the nearest centre within a quarter frame.
 */
internal fun matchDetection(candidates: List<TrackDetection>, target: TrackDetection): TrackDetection? {
    if (candidates.isEmpty()) return null
    val targetId = target.validTrackId
    val targetBox = target.box
    // Only trust ids when the target's own id is among the candidates; otherwise
    // the tracker re-identified the object and geometry has to decide.
    val idPresent = targetId != null && candidates.any { it.validTrackId == targetId }
    var best: TrackDetection? = null
    var bestIou = 0.0
    var bestHasIdMatch = false
    var nearest: TrackDetection? = null
    var nearestDist = Double.POSITIVE_INFINITY
    for (candidate in candidates) {
        if (candidate.normalizedLabel != target.normalizedLabel) continue
        val candidateId = candidate.validTrackId
        if (idPresent && candidateId != null && candidateId != targetId) continue
        val hasIdMatch = targetId != null && candidateId == targetId
        val box = candidate.box
        if (box == null || targetBox == null) {
            if (nearest == null) nearest = candidate
            continue
        }
        val overlap = iou(box, targetBox)
        if (overlap > bestIou || (hasIdMatch && !bestHasIdMatch)) {
            bestIou = overlap
            best = candidate
            bestHasIdMatch = hasIdMatch
        }
        val dx = box.centerX - targetBox.centerX
        val dy = box.centerY - targetBox.centerY
        val dist = dx * dx + dy * dy
        if (dist < nearestDist) {
            nearestDist = dist
            nearest = candidate
        }
    }
    if (best != null) return best
    return if (nearestDist <= MAX_MATCH_CENTER_DISTANCE_SQ) nearest else null
}

private fun lerpBox(a: TrackBox, b: TrackBox, factor: Double): TrackBox {
    fun lerp(from: Double, to: Double) = (from + (to - from) * factor).coerceIn(0.0, 1.0)
    return TrackBox(lerp(a.x, b.x), lerp(a.y, b.y), lerp(a.width, b.width), lerp(a.height, b.height))
}

/**
 * Extrapolate [cur] (observed at [curTime]) to [targetTime] using the velocity
 * since [prev], capped at [maxLead] so a stale box does not drift away.
 */
private fun projectDetections(
    prev: List<TrackDetection>,
    cur: List<TrackDetection>,
    prevTime: Double,
    curTime: Double,
    targetTime: Double,
    maxLead: Double,
): List<TrackDetection> {
    if (cur.isEmpty() || prev.isEmpty()) return cur
    val interval = curTime - prevTime
    if (!(interval > 0)) return cur
    val lead = targetTime - curTime
    if (lead <= 0) return cur
    val factor = min(lead, maxLead) / interval
    return cur.map { curDet ->
        val prevDet = matchDetection(prev, curDet)
        val a = prevDet?.box
        val b = curDet.box
        if (a == null || b == null) curDet else curDet.copy(box = lerpBox(a, b, 1 + factor))
    }
}

// Hold/bridge windows scale with sample spacing but are capped, so a slow
// detection cadence never keeps a stale box on screen for long.
private const val TRACK_MAX_HOLD_CAP_SECONDS = 3.0
private const val TRACK_BRIDGE_CAP_SECONDS = 4.0

private fun holdWindow(spacing: Double) = min(TRACK_MAX_HOLD_CAP_SECONDS, max(1.0, spacing * 3))

/**
 * The detections at playback time [time], interpolating boxes between the two
 * surrounding samples so the overlay follows objects smoothly, and bridging
 * single missed detection cycles. [track] must be sorted by time.
 */
fun sampleTrackAtTime(track: List<TrackFrame>, time: Double): List<TrackDetection> {
    if (track.isEmpty()) return emptyList()
    val t = if (time.isFinite()) time else 0.0
    val last = track.last()
    val tailSpacing = if (track.size > 1) last.t - track[track.size - 2].t else 0.0
    // A truncated track must not freeze its final box for the rest of the clip.
    if (t > last.t + holdWindow(tailSpacing)) return emptyList()
    // Nothing was analysed before the track starts.
    if (t < track[0].t) return emptyList()
    if (t == track[0].t) return track[0].detections
    if (t >= last.t) {
        val prev = if (track.size > 1) track[track.size - 2] else null
        return projectDetections(
            prev?.detections.orEmpty(), last.detections, prev?.t ?: Double.NaN, last.t, t,
            max(0.25, tailSpacing * 1.5),
        )
    }
    var lo = 0
    var hi = track.size - 1
    while (lo + 1 < hi) {
        val mid = (lo + hi) ushr 1
        if (track[mid].t <= t) lo = mid else hi = mid
    }
    var prev = track[lo]
    var next = track[hi]
    val bridge = min(TRACK_BRIDGE_CAP_SECONDS, max(1.2, (next.t - prev.t) * 3))
    if (next.detections.isEmpty() && prev.detections.isNotEmpty()) {
        var k = hi + 1
        while (k < track.size && track[k].t - prev.t <= bridge) {
            if (track[k].detections.isNotEmpty()) { next = track[k]; break }
            k++
        }
    } else if (prev.detections.isEmpty() && next.detections.isNotEmpty()) {
        var k = lo - 1
        while (k >= 0 && next.t - track[k].t <= bridge) {
            if (track[k].detections.isNotEmpty()) { prev = track[k]; break }
            k--
        }
    } else if (prev.detections.isEmpty() && next.detections.isEmpty()) {
        // Two or more misses in a row: bridge only if the whole gap fits.
        var before: TrackFrame? = null
        var k = lo - 1
        while (k >= 0 && next.t - track[k].t <= bridge) {
            if (track[k].detections.isNotEmpty()) { before = track[k]; break }
            k--
        }
        if (before != null) {
            k = hi + 1
            while (k < track.size && track[k].t - before.t <= bridge) {
                if (track[k].detections.isNotEmpty()) { prev = before; next = track[k]; break }
                k++
            }
        }
    }
    val span = next.t - prev.t
    if (!(span > 0)) return next.detections
    val factor = (t - prev.t) / span
    if (factor <= 0) return prev.detections
    if (factor >= 1) return next.detections
    return next.detections.flatMap { nextDet ->
        // Interpolate only observed pairs; a new object waits for its own sample.
        val prevDet = matchDetection(prev.detections, nextDet) ?: return@flatMap emptyList()
        val a = prevDet.box
        val b = nextDet.box
        if (a == null || b == null) listOf(prevDet) else listOf(nextDet.copy(box = lerpBox(a, b, factor)))
    }
}

/**
 * What the overlay draws for a clip: its detection [track] when it has one,
 * else the triggering event's [eventBoxes], held from [eventFrom] seconds on
 * (the trigger frame; drawing them over the pre-roll would box footage
 * recorded before the detection existed).
 */
data class OverlaySource(
    val track: List<TrackFrame> = emptyList(),
    val eventBoxes: List<TrackDetection> = emptyList(),
    val eventFrom: Double = 0.0,
    // Labels the cameras are configured to act on; null draws every label.
    val labelFilter: Set<String>? = null,
) {
    fun detectionsAt(time: Double): List<TrackDetection> {
        val detections = when {
            track.isNotEmpty() -> sampleTrackAtTime(track, time)
            time >= eventFrom -> eventBoxes
            else -> emptyList()
        }
        val labels = labelFilter ?: return filterObjectPriority(detections)
        return filterObjectPriority(detections.filter { it.normalizedLabel in labels })
    }
}

/**
 * Labels the overlay draws, as on the web player: motion, plus the label of
 * every enabled object rule in any camera zone that alerts or records.
 */
fun configuredOverlayLabels(settings: SystemSettings): Set<String> = buildSet {
    add("motion")
    for (camera in settings.cameras) {
        for (zone in camera.detection?.zones.orEmpty()) {
            for (rule in zone.objectRules) {
                if (!rule.enabled || !(rule.emailEnabled || rule.pushEnabled || rule.recordOnDetect)) continue
                rule.label?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }
}

/**
 * Camera zones the clip's detections fell in: the event's detections plus any
 * later ones on the track. Sound clips have none.
 */
fun recordingZoneNames(recording: Recording): List<String> {
    if (isSoundDetection(recording.source, recording.triggerType, recording.triggerLabel, recording.labels)) {
        return emptyList()
    }
    val fromEvent = recording.detections.mapNotNull { it.zoneName }
    val fromTrack = parseDetectionTrack(recording.track).flatMap { frame -> frame.detections.mapNotNull { it.zoneName } }
    return (fromEvent + fromTrack).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

// Labels that only say something happened; dropped when a specific object was detected.
private val GENERIC_TRIGGER_LABELS = setOf("motion", "alert", "human", "object", "none", "off", "continuous")

/** The overlay for [recording], or null when it has nothing to draw. */
fun overlaySource(recording: Recording): OverlaySource? {
    val track = parseDetectionTrack(recording.track)
    if (track.any { frame -> frame.detections.isNotEmpty() }) return OverlaySource(track = track)

    val boxed = recording.detections.mapNotNull { det ->
        val box = TrackBox(det.x ?: return@mapNotNull null, det.y ?: return@mapNotNull null,
            det.width ?: return@mapNotNull null, det.height ?: return@mapNotNull null)
        TrackDetection(label = det.label, confidence = det.confidence, box = box)
    }
    val specific = boxed.filter { it.normalizedLabel !in GENERIC_TRIGGER_LABELS }
    val boxes = specific.ifEmpty { boxed }
    if (boxes.isEmpty()) return null

    val startMs = parseTimestamp(recording.startedAt)?.toInstant()?.toEpochMilli()
    val eventMs = parseTimestamp(recording.event?.createdAt)?.toInstant()?.toEpochMilli()
    val from = if (startMs != null && eventMs != null) max(0.0, (eventMs - startMs) / 1000.0) else 0.0
    return OverlaySource(eventBoxes = boxes, eventFrom = from)
}
