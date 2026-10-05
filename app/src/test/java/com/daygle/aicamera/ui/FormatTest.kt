package com.daygle.aicamera.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FormatTest {

    @Test
    fun parseTimestamp_acceptsOffsetAndNaiveForms() {
        assertEquals(
            OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(2)),
            parseTimestamp("2026-01-02T03:04:05+02:00"),
        )
        // Naive timestamps are treated as UTC.
        assertEquals(
            OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
            parseTimestamp("2026-01-02T03:04:05"),
        )
    }

    @Test
    fun parseTimestamp_returnsNullForGarbage() {
        assertNull(parseTimestamp(null))
        assertNull(parseTimestamp(""))
        assertNull(parseTimestamp("yesterday"))
    }

    @Test
    fun detectionClassification() {
        assertTrue(isSoundDetection("Sound", null, null, emptyList()))
        assertTrue(isSoundDetection("front", null, null, listOf("Glass Breaking")))
        assertTrue(isMotionDetection(null, "MOTION", null, emptyList()))
        assertTrue(isMotionDetection("front", null, "movement", emptyList()))
        assertFalse(isSoundDetection("front", "object", "person", listOf("car")))
        assertFalse(isMotionDetection("front", "object", "person", listOf("car")))
    }

    @Test
    fun formatDuration_formatsMinutesAndSeconds() {
        assertEquals("1:05", formatDuration(65.9))
        assertEquals("0:00", formatDuration(-3.0))
    }

    @Test
    fun formatMotionFraction_matchesServerPills() {
        assertEquals("3.4%", formatMotionFraction(0.034))
        assertEquals("<0.1%", formatMotionFraction(0.0004))
        assertEquals("0.0%", formatMotionFraction(0.0))
        assertEquals("42%", formatMotionFraction(0.42))
        assertEquals("100%", formatMotionFraction(1.7))
    }

    @Test
    fun formatDetectionSummary_showsMotionShareWhenKnown() {
        val summary = formatDetectionSummary(
            listOf(
                com.daygle.aicamera.data.model.Detection("person", 0.92),
                com.daygle.aicamera.data.model.Detection("motion", 1.0, motionFraction = 0.034),
                com.daygle.aicamera.data.model.Detection("motion", 0.5),
            )
        )
        assertEquals("Person (92%), Motion · 3.4%, Motion (50%)", summary)
    }
}
