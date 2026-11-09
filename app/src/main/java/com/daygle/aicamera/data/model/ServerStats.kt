package com.daygle.aicamera.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Host health from `GET /api/system/resources` (admin only): the same snapshot
 * the server's web System page shows. Every field is optional; the server
 * reports null for what the host can't measure (e.g. no load average on
 * Windows, no GPU without nvidia-smi).
 */
@Serializable
data class ServerResources(
    @SerialName("cpu_percent") val cpuPercent: Double? = null,
    @SerialName("cpu_count") val cpuCount: Int? = null,
    /** 1, 5 and 15 minute load averages. */
    @SerialName("load_average") val loadAverage: List<Double>? = null,
    val memory: MemoryUsage? = null,
    /** Null when the host has no NVIDIA GPU (or nvidia-smi is unavailable). */
    val gpu: GpuStatus? = null,
    @SerialName("video_decode") val videoDecode: VideoDecodeStatus? = null,
)

/** RAM in bytes; [percent] is the share in use. */
@Serializable
data class MemoryUsage(
    val total: Long? = null,
    val available: Long? = null,
    val used: Long? = null,
    val percent: Double? = null,
)

@Serializable
data class GpuStatus(
    val count: Int = 0,
    val primary: GpuDevice? = null,
    @SerialName("warn_temp_c") val warnTempC: Int? = null,
    @SerialName("critical_temp_c") val criticalTempC: Int? = null,
)

/** One nvidia-smi reading; memory is in MiB. */
@Serializable
data class GpuDevice(
    val name: String? = null,
    @SerialName("temperature_c") val temperatureC: Double? = null,
    @SerialName("utilization_percent") val utilizationPercent: Double? = null,
    @SerialName("graphics_clock_mhz") val graphicsClockMhz: Double? = null,
    @SerialName("memory_clock_mhz") val memoryClockMhz: Double? = null,
    @SerialName("power_draw_watts") val powerDrawWatts: Double? = null,
    @SerialName("power_limit_watts") val powerLimitWatts: Double? = null,
    @SerialName("memory_used_mb") val memoryUsedMb: Double? = null,
    @SerialName("memory_total_mb") val memoryTotalMb: Double? = null,
    /** `ok`, `warn`, `critical` or `unknown`, against the server's thresholds. */
    @SerialName("thermal_status") val thermalStatus: String? = null,
)

/** Where each camera's video is decoded for detection (NVIDIA NVDEC or CPU). */
@Serializable
data class VideoDecodeStatus(
    /** `auto`, `gpu` or `cpu`, as set on the server. */
    val setting: String? = null,
    @SerialName("ffmpeg_cuda") val ffmpegCuda: Boolean = false,
    @SerialName("nvidia_gpu") val nvidiaGpu: Boolean = false,
    val cameras: List<DecodeCamera> = emptyList(),
)

@Serializable
data class DecodeCamera(
    @SerialName("camera_id") val cameraId: String? = null,
    val name: String? = null,
    /** `gpu` or `cpu`. */
    val decode: String? = null,
    /** The GPU decoder failed and the camera fell back to the CPU. */
    @SerialName("gpu_fallback") val gpuFallback: Boolean = false,
)

/** Detection and alert counts from `GET /api/stats` (admin only), optionally since a time. */
@Serializable
data class ActivityStats(
    @SerialName("total_events") val totalEvents: Int = 0,
    @SerialName("total_alerts") val totalAlerts: Int = 0,
    @SerialName("matched_object_events") val objectEvents: Int = 0,
    @SerialName("sound_detection_events") val soundEvents: Int = 0,
    @SerialName("motion_detection_events") val motionEvents: Int = 0,
    @SerialName("object_alerts") val objectAlerts: Int = 0,
    @SerialName("sound_alerts") val soundAlerts: Int = 0,
    /** Detections per label, most frequent first. */
    val objects: List<LabelCount> = emptyList(),
    @SerialName("total_cameras") val totalCameras: Int = 0,
)

@Serializable
data class LabelCount(
    val label: String = "",
    val count: Int = 0,
    @SerialName("max_confidence") val maxConfidence: Double? = null,
)

/**
 * Disk space and media usage from `GET /api/system/storage` (admin only).
 * Older servers don't have the endpoint (404), so the app hides the card.
 */
@Serializable
data class StorageStatus(
    val volumes: List<StorageVolume> = emptyList(),
    val usage: StorageUsage = StorageUsage(),
    val retention: StorageRetention = StorageRetention(),
)

/** One filesystem holding server data, listed once with every folder on it. */
@Serializable
data class StorageVolume(
    @SerialName("total_bytes") val totalBytes: Long? = null,
    @SerialName("used_bytes") val usedBytes: Long? = null,
    @SerialName("free_bytes") val freeBytes: Long? = null,
    /** Used share of the space usable by the server, as `df` reports it. */
    val percent: Double? = null,
    /** `recordings`, `snapshots`, `events` and/or `database`. */
    val holds: List<String> = emptyList(),
)

@Serializable
data class StorageUsage(
    @SerialName("recordings_bytes") val recordingsBytes: Long? = null,
    @SerialName("snapshots_bytes") val snapshotsBytes: Long? = null,
    @SerialName("events_bytes") val eventsBytes: Long? = null,
    @SerialName("database_bytes") val databaseBytes: Long? = null,
)

/** The limits the server's purge job keeps recordings within. */
@Serializable
data class StorageRetention(
    @SerialName("max_storage_bytes") val maxStorageBytes: Long? = null,
    @SerialName("retention_days") val retentionDays: Int? = null,
    @SerialName("auto_purge_enabled") val autoPurgeEnabled: Boolean? = null,
)
