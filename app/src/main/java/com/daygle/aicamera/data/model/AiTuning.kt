package com.daygle.aicamera.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The server's AI Insights (`GET /api/settings/ai-insights`, admins only):
 * what the AI model's verdicts suggest changing in the detector's settings.
 * Each list is empty until the AI has judged enough detections.
 */
@Serializable
data class AiInsightsResponse(
    val tuning: List<ConfidenceSuggestion> = emptyList(),
    @SerialName("ignore_areas") val ignoreAreas: List<IgnoreAreaSuggestion> = emptyList(),
    @SerialName("ignored_areas") val ignoredAreas: List<IgnoredArea> = emptyList(),
    @SerialName("frequent_tags") val frequentTags: List<FrequentTags> = emptyList(),
    @SerialName("tuning_window_days") val tuningWindowDays: Int = 14,
    @SerialName("tags_window_days") val tagsWindowDays: Int = 7,
)

/** Raise a label's minimum confidence on a camera: the AI kept rejecting detections below it. */
@Serializable
data class ConfidenceSuggestion(
    @SerialName("camera_id") val cameraId: String,
    @SerialName("camera_name") val cameraName: String = "",
    val label: String,
    val zones: List<String> = emptyList(),
    @SerialName("current_min_confidence") val currentMinConfidence: Double = 0.0,
    @SerialName("min_confidence") val minConfidence: Double,
    @SerialName("rejected_below") val rejectedBelow: Int = 0,
    @SerialName("judged_below") val judgedBelow: Int = 0,
    @SerialName("confirmed_lost") val confirmedLost: Int = 0,
    @SerialName("confirmed_total") val confirmedTotal: Int = 0,
)

/** A rectangle of the frame, each value a 0-1 fraction. */
@Serializable
data class FrameArea(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/** A spot where the AI keeps saying [label] is not there (a bush read as a dog). */
@Serializable
data class IgnoreAreaSuggestion(
    @SerialName("camera_id") val cameraId: String,
    @SerialName("camera_name") val cameraName: String = "",
    val label: String,
    val zones: List<String> = emptyList(),
    val area: FrameArea,
    val rejected: Int = 0,
    val confirmed: Int = 0,
    @SerialName("sample_event_id") val sampleEventId: Int? = null,
)

/** An ignore area in force on one zone; [index] is its position in the zone. */
@Serializable
data class IgnoredArea(
    @SerialName("camera_id") val cameraId: String,
    @SerialName("camera_name") val cameraName: String = "",
    @SerialName("zone_id") val zoneId: String,
    @SerialName("zone_name") val zoneName: String = "",
    val index: Int,
    val label: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    /** False once the zone was redrawn away from the spot: it then has no effect. */
    @SerialName("in_zone") val inZone: Boolean = true,
) {
    val area: FrameArea get() = FrameArea(x, y, width, height)
}

/** AI tags named often on a camera that no detector class or tag alert covers. */
@Serializable
data class FrequentTags(
    @SerialName("camera_id") val cameraId: String,
    @SerialName("camera_name") val cameraName: String = "",
    val tags: List<TagCount> = emptyList(),
)

@Serializable
data class TagCount(val tag: String, val count: Int)

@Serializable
data class ApplyConfidenceRequest(
    @SerialName("camera_id") val cameraId: String,
    val label: String,
    @SerialName("min_confidence") val minConfidence: Double,
)

@Serializable
data class IgnoreAreaRequest(
    @SerialName("camera_id") val cameraId: String,
    val label: String,
    val area: FrameArea,
)

@Serializable
data class RemoveIgnoreAreaRequest(
    @SerialName("camera_id") val cameraId: String,
    @SerialName("zone_id") val zoneId: String,
    val index: Int,
)
