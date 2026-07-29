package com.daygle.aicamera.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val summary: CameraHealthSummary = CameraHealthSummary(),
)

@Serializable
data class CameraHealthState(
    val online: Boolean = true,
)

@Serializable
data class CameraHealthSummary(
    val online: Int = 0,
    val offline: Int = 0,
    val total: Int = 0,
)

@Serializable
data class StatusResponse(
    val status: String? = null,
    val mode: String? = null,
    @SerialName("camera_id") val cameraId: String? = null,
    @SerialName("camera_name") val cameraName: String? = null,
    @SerialName("ai_backend") val aiBackend: String? = null,
    @SerialName("ai_available") val aiAvailable: Boolean = false,
    @SerialName("ai_error") val aiError: String? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Double = 0.0,
    val resolution: Resolution = Resolution(),
)

@Serializable
data class Resolution(
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class Detection(
    val label: String = "",
    val confidence: Double = 0.0,
)

@Serializable
data class Event(
    val id: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val source: String? = null,
    @SerialName("snapshot_path") val snapshotPath: String? = null,
    @SerialName("thumbnail_path") val thumbnailPath: String? = null,
    @SerialName("alert_triggered") val alertTriggered: Int = 0,
    val detections: List<Detection> = emptyList(),
    @SerialName("recording_status") val recordingStatus: String? = null,
) {
    val alerted: Boolean get() = alertTriggered != 0
    val topLabel: String? get() = detections.maxByOrNull { it.confidence }?.label
}

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
)
