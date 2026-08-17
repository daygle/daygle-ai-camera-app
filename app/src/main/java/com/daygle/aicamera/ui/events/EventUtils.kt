package com.daygle.aicamera.ui.events

import com.daygle.aicamera.data.model.Event
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

internal fun dateRangeLabel(start: LocalDate?, end: LocalDate?): String {
    if (start == null && end == null) return "Anytime"
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
    if (start != null && end != null) {
        return "${start.format(formatter)} - ${end.format(formatter)}"
    }
    return if (start != null) "From ${start.format(formatter)}" else "Until ${end!!.format(formatter)}"
}
