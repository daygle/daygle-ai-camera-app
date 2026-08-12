package com.daygle.aicamera.ui

import android.text.format.DateFormat
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Whether times should be shown in 24-hour format, sourced from the app's
 * "24-hour time" preference and provided at the app root (see DaygleTheme).
 */
val LocalUse24Hour = staticCompositionLocalOf { false }

private val SOUND_CLASS_IDS = setOf(
    "cat_meow", "dog_bark", "glass_breaking", "smoke_alarm",
    "baby_crying", "doorbell", "car_alarm", "loud_bang"
)

/** Returns true if the label identifies a sound category. */
fun isSoundLabel(label: String?): Boolean {
    val normalized = label?.trim()?.lowercase(Locale.getDefault())?.replace(" ", "_") ?: return false
    return normalized in SOUND_CLASS_IDS || normalized.contains("sound")
}

/** Returns true if the label identifies a motion detection. */
fun isMotionLabel(label: String?): Boolean {
    val normalized = label?.trim()?.lowercase(Locale.getDefault())?.replace(" ", "_") ?: return false
    return normalized.contains("motion") || normalized.contains("movement")
}

/**
 * Localized date + time formatter that honors the app's 24-hour preference.
 * Builds the pattern from a skeleton so the hour cycle (H vs h) is forced while
 * the rest stays locale-appropriate.
 */
private fun displayFormatter(use24Hour: Boolean): DateTimeFormatter {
    val locale = Locale.getDefault()
    val skeleton = "yMMMd" + if (use24Hour) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** Localized time-of-day formatter that honors the app's 24-hour preference. */
fun timeFormatter(use24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter {
    val skeleton = if (use24Hour) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** Render an ISO-8601 timestamp from the server in the device's local time zone. */
fun formatTimestamp(iso: String?, use24Hour: Boolean): String {
    if (iso.isNullOrBlank()) return "-"
    return try {
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(displayFormatter(use24Hour))
    } catch (_: Exception) {
        iso
    }
}

/** 
 * Map technical terms to user-friendly ones (e.g. 'rtsp' -> 'Object') 
 * and ensure title-casing.
 */
fun formatEventLabel(text: String?): String {
    if (text.isNullOrBlank()) return ""
    val normalized = text.replace('_', ' ')
    val mapped = when (normalized.lowercase(Locale.getDefault())) {
        "rtsp" -> "Object"
        else -> normalized
    }
    return mapped.split(' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

/** Human-friendly duration such as `1:05` or `0:42`. */
fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val minutes = total / 60
    val secs = total % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
}
