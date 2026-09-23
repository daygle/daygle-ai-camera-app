package com.daygle.aicamera.ui.events

import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.metadataDouble
import com.daygle.aicamera.data.model.metadataString
import com.daygle.aicamera.ui.isMotionLabel
import com.daygle.aicamera.ui.isSoundLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun isMotionEvent(event: Event): Boolean =
    event.source?.lowercase() == "motion" ||
        event.triggerType?.lowercase() == "motion" ||
        isMotionLabel(event.triggerLabel) ||
        event.detections.any { isMotionLabel(it.label) }

internal fun isSoundEvent(event: Event): Boolean =
    event.source?.lowercase() == "sound" ||
        event.triggerType?.lowercase() == "sound" ||
        isSoundLabel(event.triggerLabel) ||
        event.detections.any { isSoundLabel(it.label) }

/** Behavioural-intelligence events (tripwires, loitering, unusual time-of-day). */
internal fun Event.isBehaviourEvent(): Boolean =
    source?.lowercase() == "behaviour"

/**
 * Camera id that produced this event. Behaviour events carry `source=behaviour`;
 * their real camera lives in `metadata.camera_id`, so camera filters keep working.
 */
internal fun Event.filterCameraId(): String? =
    if (isBehaviourEvent()) metadataString("camera_id") else source

/**
 * Human context for a behaviour event - "Person crossed Driveway Line (inbound)
 * in Front Yard". Prefers the alert message the server composes; for events
 * without a fired alert falls back to the event metadata. Null for
 * non-behaviour events without a zone.
 */
internal fun Event.behaviourContext(): String? {
    if (!isBehaviourEvent()) return null
    alert?.message?.takeIf { it.isNotBlank() }?.let { return it }
    val zone = metadataString("zone_name") ?: return null
    val subject = metadataString("label")?.replaceFirstChar { it.uppercaseChar() } ?: "Object"
    return when (metadataString("source")) {
        "line-crossing" -> "$subject crossed ${metadataString("tripwire_name") ?: "a tripwire"} in $zone"
        "loiter" -> "$subject loitering in $zone (${metadataDouble("dwell_seconds")?.toInt() ?: 0}s)"
        "time_of_day" -> "$subject in $zone at an unusual time"
        else -> "$subject in $zone"
    }
}

internal fun dateRangeLabel(start: LocalDate?, end: LocalDate?): String {
    if (start == null && end == null) return "Anytime"
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
    if (start != null && end != null) {
        return "${start.format(formatter)} - ${end.format(formatter)}"
    }
    return if (start != null) "From ${start.format(formatter)}" else "Until ${end!!.format(formatter)}"
}
