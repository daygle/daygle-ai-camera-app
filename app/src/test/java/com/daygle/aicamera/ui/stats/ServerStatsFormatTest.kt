package com.daygle.aicamera.ui.stats

import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.model.ActivityStats
import com.daygle.aicamera.data.model.DecodeCamera
import com.daygle.aicamera.data.model.GpuDevice
import com.daygle.aicamera.data.model.GpuStatus
import com.daygle.aicamera.data.model.MemoryUsage
import com.daygle.aicamera.data.model.ServerResources
import com.daygle.aicamera.data.model.StorageRetention
import com.daygle.aicamera.data.model.StorageStatus
import com.daygle.aicamera.data.model.StorageUsage
import com.daygle.aicamera.data.model.VideoDecodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerStatsFormatTest {

    private val gib = 1024L * 1024 * 1024

    @Test
    fun decodesTheServersResourcesPayload() {
        // Shape of GET /api/system/resources, numbers as the server sends them.
        val json = """
            {"cpu_percent": 12.5, "cpu_count": 8, "load_average": [0.5, 0.75, 1],
             "memory": {"total": 17179869184, "available": 8589934592, "used": 8589934592, "percent": 50.0},
             "gpu": {"count": 1, "devices": [{}], "warn_temp_c": 85, "critical_temp_c": 90,
                     "primary": {"name": "Tesla P4", "temperature_c": 61, "utilization_percent": 34,
                                 "graphics_clock_mhz": 1113, "memory_clock_mhz": 2999, "power_draw_watts": 23.4,
                                 "power_limit_watts": 75.0, "memory_used_mb": 2048, "memory_total_mb": 7680,
                                 "thermal_status": "ok"}},
             "video_decode": {"setting": "auto", "ffmpeg_cuda": true, "nvidia_gpu": true,
                              "cameras": [{"camera_id": "front", "name": "Front", "decode": "gpu", "gpu_fallback": false}]}}
        """
        val res = SessionManager.json.decodeFromString<ServerResources>(json)
        assertEquals(8, res.cpuCount)
        assertEquals(listOf(0.5, 0.75, 1.0), res.loadAverage)
        assertEquals(17179869184L, res.memory?.total)
        assertEquals("Tesla P4", res.gpu?.primary?.name)
        assertEquals(61.0, res.gpu?.primary?.temperatureC)
        assertEquals("gpu", res.videoDecode?.cameras?.single()?.decode)
    }

    @Test
    fun decodesAHostWithoutGpuOrLoadAverage() {
        val res = SessionManager.json.decodeFromString<ServerResources>(
            """{"cpu_percent": null, "cpu_count": 4, "load_average": null, "memory": null, "gpu": null}""",
        )
        assertNull(res.gpu)
        assertNull(gpuTile(res.gpu))
        assertEquals("–", cpuTile(res).value)
        assertEquals("–", loadTile(res).value)
        assertEquals("Memory usage", ramTile(res).detail)
    }

    @Test
    fun decodesActivityStats() {
        val stats = SessionManager.json.decodeFromString<ActivityStats>(
            """{"total_events": 42, "total_alerts": 7, "matched_object_events": 30, "sound_detection_events": 4,
                "motion_detection_events": 8, "object_alerts": 6, "sound_alerts": 1,
                "objects": [{"label": "person", "count": 20, "max_confidence": 0.97}], "total_cameras": 3}""",
        )
        assertEquals(42, stats.totalEvents)
        assertEquals(30, stats.objectEvents)
        assertEquals("person", stats.objects.single().label)
    }

    @Test
    fun formatsGigabytesLikeTheWebPage() {
        assertEquals("1.5 GB", formatGb(1.5 * gib))
        assertEquals("16 GB", formatGb(16.0 * gib))
    }

    @Test
    fun cpuLoadAndRamTiles() {
        val res = ServerResources(
            cpuPercent = 12.5,
            cpuCount = 1,
            loadAverage = listOf(0.5, 0.75, 1.0),
            memory = MemoryUsage(total = 16 * gib, used = 8 * gib, percent = 50.0),
        )
        assertEquals(StatTile("CPU", "12.5%", "1 core", 0.125f), cpuTile(res))
        assertEquals("0.50", loadTile(res).value)
        assertEquals("5 min 0.75 · 15 min 1.00", loadTile(res).detail)
        assertEquals(StatTile("RAM", "50%", "8.0 GB / 16 GB used", 0.5f), ramTile(res))
    }

    @Test
    fun gpuTileShowsUsageOrAThermalWarning() {
        val device = GpuDevice(
            name = "Tesla P4", temperatureC = 61.4, utilizationPercent = 34.0,
            graphicsClockMhz = 1113.0, powerDrawWatts = 23.4, powerLimitWatts = 75.0, thermalStatus = "ok",
        )
        val ok = gpuTile(GpuStatus(primary = device, criticalTempC = 90))!!
        assertEquals("61°C", ok.value)
        assertEquals("34% util · 1113 MHz · 23/75 W", ok.detail)
        assertEquals(StatLevel.NORMAL, ok.level)

        val hot = gpuTile(GpuStatus(primary = device.copy(thermalStatus = "critical"), criticalTempC = 90))!!
        assertEquals(StatLevel.CRITICAL, hot.level)
        assertEquals("At 90°C throttle limit: reduce load or improve airflow", hot.detail)
        assertEquals(StatLevel.WARN, gpuTile(GpuStatus(primary = device.copy(thermalStatus = "warn")))!!.level)
    }

    @Test
    fun vramTileFlagsANearlyFullPool() {
        fun vram(used: Double) = vramTile(GpuStatus(primary = GpuDevice(memoryUsedMb = used, memoryTotalMb = 1000.0)))!!
        assertEquals(StatLevel.NORMAL, vram(500.0).level)
        assertEquals(StatLevel.WARN, vram(900.0).level)
        assertEquals(StatLevel.CRITICAL, vram(970.0).level)
        assertEquals("50%", vram(500.0).value)
        assertNull(vramTile(GpuStatus(primary = GpuDevice(memoryUsedMb = 1.0, memoryTotalMb = null))))
    }

    @Test
    fun decodeSummaryMatchesTheWebPage() {
        val cams = listOf(DecodeCamera(decode = "gpu"), DecodeCamera(decode = "cpu"))
        assertEquals(
            "Setting: Automatic. 1 of 2 cameras decoding on the GPU.",
            decodeSummary(VideoDecodeStatus(setting = "auto", ffmpegCuda = true, cameras = cams)),
        )
        assertEquals(
            "Setting: GPU. No camera is connected yet.",
            decodeSummary(VideoDecodeStatus(setting = "gpu", ffmpegCuda = true)),
        )
        assertEquals(
            "Setting: CPU. This server's ffmpeg can't decode on an NVIDIA GPU, so video is decoded on the CPU.",
            decodeSummary(VideoDecodeStatus(setting = "cpu", ffmpegCuda = false, cameras = cams)),
        )
    }

    @Test
    fun decodesTheServersStoragePayload() {
        // Shape of GET /api/system/storage.
        val json = """
            {"volumes": [{"total_bytes": 107374182400, "used_bytes": 96636764160, "free_bytes": 10737418240,
                          "percent": 90.0, "holds": ["recordings", "snapshots", "events", "database"]}],
             "usage": {"recordings_bytes": 85899345920, "snapshots_bytes": 2147483648, "events_bytes": 0, "database_bytes": 52428800},
             "retention": {"max_storage_bytes": 107374182400, "retention_days": 30, "auto_purge_enabled": true},
             "usage_cached_seconds": 60.0}
        """
        val status = SessionManager.json.decodeFromString<StorageStatus>(json)
        val volume = status.volumes.single()
        assertEquals(107374182400L, volume.totalBytes)
        assertEquals(listOf("recordings", "snapshots", "events", "database"), volume.holds)
        assertEquals(85899345920L, status.usage.recordingsBytes)
        assertEquals(30, status.retention.retentionDays)
    }

    @Test
    fun formatsBytesInBinaryUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 MB", formatBytes(1536L * 1024))
        assertEquals("250 GB", formatBytes(250 * gib))
        assertEquals("2.0 TB", formatBytes(2048 * gib))
    }

    @Test
    fun storageWarnsAt85AndIsCriticalAt95Percent() {
        assertEquals(StatLevel.NORMAL, storageLevel(null))
        assertEquals(StatLevel.NORMAL, storageLevel(84.9))
        assertEquals(StatLevel.WARN, storageLevel(85.0))
        assertEquals(StatLevel.CRITICAL, storageLevel(95.0))
    }

    @Test
    fun describesVolumesAndRetention() {
        assertEquals("Recordings · Database", volumeLabel(listOf("recordings", "database")))
        assertEquals("Disk", volumeLabel(emptyList()))
        assertEquals("90 GB of 100 GB used · 10 GB free", volumeDetail(90 * gib, 100 * gib, 10 * gib))
        assertEquals("10 GB free", volumeDetail(null, null, 10 * gib))
        assertEquals(
            "Recordings use 12 GB of the 20 GB limit, kept for 14 days.",
            storageSummary(StorageStatus(usage = StorageUsage(recordingsBytes = 12 * gib), retention = StorageRetention(20 * gib, 14, true))),
        )
        assertEquals(
            "Recordings use 1.0 KB, kept for 1 day, auto-purge is off.",
            storageSummary(StorageStatus(usage = StorageUsage(recordingsBytes = 1024), retention = StorageRetention(null, 1, false))),
        )
    }
}
