package com.daygle.aicamera.ui.stats

import com.daygle.aicamera.data.model.GpuStatus
import com.daygle.aicamera.data.model.ServerResources
import com.daygle.aicamera.data.model.VideoDecodeStatus
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Text and severity for the Server Stats tiles, matching the server's web
 * System page (web/system.js) so both read the same.
 */

/** How a tile is tinted: normal, approaching a limit, or at it. */
enum class StatLevel { NORMAL, WARN, CRITICAL }

/** One stat tile: a headline [value], a [detail] line and, for usage, a 0-1 [fraction] bar. */
data class StatTile(
    val label: String,
    val value: String,
    val detail: String,
    val fraction: Float? = null,
    val level: StatLevel = StatLevel.NORMAL,
)

private const val DASH = "–"
private const val VRAM_WARN_PERCENT = 90
private const val VRAM_CRITICAL_PERCENT = 97
private const val DEFAULT_CRITICAL_TEMP_C = 90

/** Binary gigabytes, as operating systems report RAM: one decimal below 10 GB. */
internal fun formatGb(bytes: Double): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 10) "${gb.roundToInt()} GB" else String.format(Locale.US, "%.1f GB", gb)
}

private fun Double.percentText(): String =
    if (this == Math.floor(this)) "${toInt()}%" else String.format(Locale.US, "%.1f%%", this)

internal fun cpuTile(res: ServerResources): StatTile {
    val cpu = res.cpuPercent
    val cores = res.cpuCount
    return StatTile(
        label = "CPU",
        value = cpu?.percentText() ?: DASH,
        detail = cores?.let { "$it core${if (it == 1) "" else "s"}" } ?: "Processor usage",
        fraction = cpu?.let { (it / 100).toFloat().coerceIn(0f, 1f) },
    )
}

internal fun loadTile(res: ServerResources): StatTile {
    val load = res.loadAverage.orEmpty()
    return StatTile(
        label = "Load",
        value = load.firstOrNull()?.let { String.format(Locale.US, "%.2f", it) } ?: DASH,
        // The headline is the 1-minute average; the detail adds 5 and 15.
        detail = if (load.size == 3) {
            String.format(Locale.US, "5 min %.2f · 15 min %.2f", load[1], load[2])
        } else {
            "1 / 5 / 15 min average"
        },
    )
}

internal fun ramTile(res: ServerResources): StatTile {
    val mem = res.memory
    val used = mem?.used
    val total = mem?.total
    return StatTile(
        label = "RAM",
        value = mem?.percent?.percentText() ?: DASH,
        detail = if (used != null && total != null) {
            "${formatGb(used.toDouble())} / ${formatGb(total.toDouble())} used"
        } else {
            "Memory usage"
        },
        fraction = mem?.percent?.let { (it / 100).toFloat().coerceIn(0f, 1f) },
    )
}

/** The GPU tile, or null without an NVIDIA GPU (the web page hides it then too). */
internal fun gpuTile(gpu: GpuStatus?): StatTile? {
    val primary = gpu?.primary ?: return null
    val level = when (primary.thermalStatus) {
        "critical" -> StatLevel.CRITICAL
        "warn" -> StatLevel.WARN
        else -> StatLevel.NORMAL
    }
    val criticalTemp = gpu.criticalTempC ?: DEFAULT_CRITICAL_TEMP_C
    val detail = when (level) {
        StatLevel.CRITICAL -> "At $criticalTemp°C throttle limit: reduce load or improve airflow"
        StatLevel.WARN -> "Approaching $criticalTemp°C throttle limit: check airflow"
        StatLevel.NORMAL -> listOfNotNull(
            primary.utilizationPercent?.let { "${it.roundToInt()}% util" },
            primary.graphicsClockMhz?.let { "${it.roundToInt()} MHz" },
            primary.powerDrawWatts?.let { draw ->
                primary.powerLimitWatts?.let { "${draw.roundToInt()}/${it.roundToInt()} W" } ?: "${draw.roundToInt()} W"
            },
        ).joinToString(" · ").ifEmpty { "Graphics card" }
    }
    return StatTile(
        label = primary.name?.takeIf { it.isNotBlank() }?.let { "GPU · $it" } ?: "GPU",
        value = primary.temperatureC?.let { "${it.roundToInt()}°C" } ?: DASH,
        detail = detail,
        fraction = primary.utilizationPercent?.let { (it / 100).toFloat().coerceIn(0f, 1f) },
        level = level,
    )
}

/** The GPU memory tile, or null when there is no GPU or it doesn't report memory. */
internal fun vramTile(gpu: GpuStatus?): StatTile? {
    val primary = gpu?.primary ?: return null
    val used = primary.memoryUsedMb ?: return null
    val total = primary.memoryTotalMb?.takeIf { it > 0 } ?: return null
    val percent = (used / total * 100).roundToInt()
    // A nearly full VRAM pool makes inference run out of memory.
    val level = when {
        percent >= VRAM_CRITICAL_PERCENT -> StatLevel.CRITICAL
        percent >= VRAM_WARN_PERCENT -> StatLevel.WARN
        else -> StatLevel.NORMAL
    }
    val mib = 1024.0 * 1024.0
    return StatTile(
        label = "VRAM",
        value = "$percent%",
        detail = "${formatGb(used * mib)} / ${formatGb(total * mib)} used",
        fraction = (percent / 100f).coerceIn(0f, 1f),
        level = level,
    )
}

/** One line on where cameras decode video, as the web System page words it. */
internal fun decodeSummary(status: VideoDecodeStatus): String {
    val setting = when (status.setting) {
        "gpu" -> "GPU"
        "cpu" -> "CPU"
        else -> "Automatic"
    }
    val cameras = status.cameras
    val onGpu = cameras.count { it.decode == "gpu" }
    val detail = when {
        !status.ffmpegCuda -> "This server's ffmpeg can't decode on an NVIDIA GPU, so video is decoded on the CPU."
        cameras.isEmpty() -> "No camera is connected yet."
        else -> "$onGpu of ${cameras.size} camera${if (cameras.size == 1) "" else "s"} decoding on the GPU."
    }
    return "Setting: $setting. $detail"
}
