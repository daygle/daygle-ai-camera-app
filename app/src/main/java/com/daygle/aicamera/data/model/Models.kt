package com.daygle.aicamera.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Data models mirroring the JSON returned by the `daygle-ai-camera` FastAPI
 * server. Every model is lenient (the shared [kotlinx.serialization.json.Json]
 * instance is configured with `ignoreUnknownKeys = true`), so the server can
 * add fields without breaking the app.
 */

@Serializable
data class CamerasResponse(
    val cameras: List<Camera> = emptyList(),
)

@Serializable
data class Camera(
    val id: String = "",
    val name: String? = null,
    val enabled: Boolean = true,
    val backend: String? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id
}

@Serializable
data class CameraHealthResponse(
    val cameras: Map<String, CameraHealthState> = emptyMap(),
)

@Serializable
data class CameraHealthState(
    val online: Boolean = true,
)

@Serializable
data class Detection(
    val label: String = "",
    val confidence: Double = 0.0,
)

/**
 * An alert is the notification fired for an event (alert : event = 1:1). The
 * server embeds the strongest alert for an event as [Event.alert].
 */
@Serializable
data class Alert(
    val id: Int = 0,
    @SerialName("event_id") val eventId: Int? = null,
    @SerialName("recording_id") val recordingId: Int? = null,
    @SerialName("rule_name") val ruleName: String? = null,
    val label: String? = null,
    val confidence: Double = 0.0,
    val message: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Event(
    val id: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val source: String? = null,
    @SerialName("snapshot_path") val snapshotPath: String? = null,
    @SerialName("thumbnail_path") val thumbnailPath: String? = null,
    @SerialName("alert_triggered") val alertTriggered: Int = 0,
    @SerialName("trigger_type") val triggerType: String? = null,
    @SerialName("trigger_label") val triggerLabel: String? = null,
    val detections: List<Detection> = emptyList(),
    @SerialName("recording_status") val recordingStatus: String? = null,
    val recordings: List<Recording> = emptyList(),
    // The clip this event belongs to (recording : events = 1:many) and the
    // strongest alert fired for it (alert : event = 1:1), if any.
    @SerialName("recording_id") val recordingId: Int? = null,
    val alert: Alert? = null,
    // Whether an annotated snapshot can be opened for this event
    // (GET /api/events/{id}/snapshot). Sound/frameless events have none.
    @SerialName("has_snapshot") val hasSnapshot: Boolean = false,
    val metadata: Map<String, JsonElement> = emptyMap(),
) {
    val alerted: Boolean get() = alert != null
    val topLabel: String? get() = triggerLabel ?: triggerType
        ?: detections.maxByOrNull { it.confidence }?.label
        ?: metadataLabel()
}

/** The free-form label some servers embed under `metadata.label`. */
fun Event.metadataLabel(): String? =
    (metadata["label"] as? JsonPrimitive)?.contentOrNull

/**
 * The server's ntfy push configuration (`GET /api/settings/alert-push`). For a
 * non-admin (viewer) session the server redacts `password`, so the app also
 * lets the user supply ntfy credentials manually.
 */
@Serializable
data class PushSettings(
    val enabled: Boolean = false,
    @SerialName("server_url") val serverUrl: String? = null,
    val topic: String? = null,
    val username: String? = null,
    val password: String? = null,
    val priority: String? = null,
)

@Serializable
data class Recording(
    val id: Int = 0,
    @SerialName("camera_id") val cameraId: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    val source: String? = null,
    @SerialName("trigger_type") val triggerType: String? = null,
    @SerialName("trigger_label") val triggerLabel: String? = null,
    @SerialName("media_ready") val mediaReady: Boolean = false,
    val labels: List<String> = emptyList(),
    val detections: List<Detection> = emptyList(),
    @SerialName("label_confidences") val labelConfidences: Map<String, Double> = emptyMap(),
    // Every event that occurred during this clip (recording : events = 1:many).
    val events: List<Event> = emptyList(),
) {
    val topLabel: String? get() = triggerLabel ?: triggerType ?: detections.maxByOrNull { it.confidence }?.label ?: labels.firstOrNull()
}
