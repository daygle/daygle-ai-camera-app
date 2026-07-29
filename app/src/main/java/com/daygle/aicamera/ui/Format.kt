package com.daygle.aicamera.ui

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val displayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())

/** Render an ISO-8601 timestamp from the server in the device's local time zone. */
fun formatTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(displayFormatter)
    } catch (_: Exception) {
        iso
    }
}

/** Human-friendly duration such as `1:05` or `0:42`. */
fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val minutes = total / 60
    val secs = total % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
}

/** Uptime rendered compactly, e.g. `2h 14m` or `47s`. */
fun formatUptime(seconds: Double): String {
    val duration = Duration.ofSeconds(seconds.toLong().coerceAtLeast(0))
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    val secs = duration.seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
