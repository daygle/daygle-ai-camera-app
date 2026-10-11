package com.daygle.aicamera.ui.player

import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.model.Recording
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The playback bar's segments match the web player's (`web/clip_timeline.js`). */
class ClipTimelineTest {

    private val json = SessionManager.json

    private fun recording(body: String) = json.decodeFromString<Recording>(body)

    @Test
    fun segmentsComeFromLocalizedTrackSamples() {
        val rec = recording(
            """{"id": 1, "started_at": "2026-10-11T21:05:00+00:00",
                "event": {"id": 9, "created_at": "2026-10-11T21:05:15+00:00"},
                "track": [{"t": 1.0, "detections": []}, {"t": 15.0, "detections": [{"label": "car"}]},
                          {"t": 30.0, "detections": []}, {"t": 56.0, "detections": [{"label": "person"}]}],
                "extensions": {"original_end": "2026-10-11T21:05:45+00:00",
                  "runs": [{"start": "2026-10-11T21:05:40+00:00", "end": "2026-10-11T21:05:50+00:00",
                            "reason": "object", "label": "car"}]}}"""
        )
        val model = clipTimelineModel(rec, 85.7)!!
        assertEquals(15.0, model.preRoll, 1e-6)
        assertEquals(41.0, model.event, 1e-6)
        assertEquals(29.7, model.tail, 1e-6)
        assertEquals(15.0, model.triggerAt!!, 1e-6)
        assertEquals(1, model.extensions.size)
        assertEquals(40.0, model.extensions[0].start, 1e-6)
        assertEquals(50.0, model.extensions[0].end, 1e-6)
        assertEquals(45.0, model.originalEnd!!, 1e-6)
    }

    @Test
    fun clipWithoutLocalizedDetectionsGetsNoBar() {
        assertNull(clipTimelineModel(recording("""{"id": 1}"""), 30.0))
        assertNull(clipTimelineModel(recording("""{"id": 1, "track": [{"t": 2, "detections": []}]}"""), 30.0))
        assertNull(clipTimelineModel(recording("""{"id": 1, "track": [{"t": 2, "detections": [{}]}]}"""), 0.0))
    }

    @Test
    fun singleSampleIsASingleDetection() {
        val model = clipTimelineModel(
            recording("""{"id": 1, "track": [{"t": 4, "detections": [{"label": "cat"}]}]}"""), 20.0
        )!!
        assertTrue(model.singleDetection)
        assertNull(model.triggerAt)
    }

    @Test
    fun formatsLikeTheWebLegend() {
        assertEquals("8.4s", formatClipSeconds(8.4))
        assertEquals("41s", formatClipSeconds(41.0))
        assertEquals("1:05", formatClipSeconds(65.0))
    }
}
