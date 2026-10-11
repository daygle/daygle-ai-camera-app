package com.daygle.aicamera.ui.player

import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.model.Recording
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Overlay playback matches the web player's `web/overlay.js`. */
class DetectionTrackTest {

    private fun car(x: Double, trackId: Int? = null) =
        TrackDetection(label = "car", confidence = 0.9, box = TrackBox(x, 0.2, 0.2, 0.2), trackId = trackId)

    @Test
    fun parsesTrackAndSkipsMalformedDetections() {
        val recording = SessionManager.json.decodeFromString<Recording>(
            """{"id": 1, "track": [
                {"t": 1.5, "detections": [{"label": "car", "confidence": 0.8,
                  "box": {"x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4}, "track_id": 7, "motion_state": "still"},
                  {"label": "person", "box": [1, 2, 3, 4]}]},
                {"t": 0.5, "detections": []}]}"""
        )
        val track = parseDetectionTrack(recording.track)
        assertEquals(listOf(0.5, 1.5), track.map { it.t })
        val det = track[1].detections.single()
        assertEquals("car", det.label)
        assertEquals(7, det.validTrackId)
        assertEquals("still", det.motionState)
        assertEquals(0.3, det.box!!.width, 1e-9)
    }

    @Test
    fun interpolatesBetweenSamples() {
        val track = listOf(TrackFrame(1.0, listOf(car(0.1))), TrackFrame(2.0, listOf(car(0.3))))
        val mid = sampleTrackAtTime(track, 1.5).single()
        assertEquals(0.2, mid.box!!.x, 1e-9)
    }

    @Test
    fun drawsNothingBeforeTrackOrLongAfterIt() {
        val track = listOf(TrackFrame(1.0, listOf(car(0.1))), TrackFrame(2.0, listOf(car(0.3))))
        assertTrue(sampleTrackAtTime(track, 0.5).isEmpty())
        assertTrue(sampleTrackAtTime(track, 10.0).isEmpty())
    }

    @Test
    fun bridgesASingleMissedCycle() {
        val track = listOf(
            TrackFrame(1.0, listOf(car(0.1))),
            TrackFrame(1.5, emptyList()),
            TrackFrame(2.0, listOf(car(0.3))),
        )
        assertEquals(0.15, sampleTrackAtTime(track, 1.25).single().box!!.x, 1e-9)
    }

    @Test
    fun trackIdKeepsCrossingObjectsApart() {
        val prev = listOf(car(0.1, trackId = 1), car(0.5, trackId = 2))
        assertEquals(2, matchDetection(prev, car(0.45, trackId = 2))!!.trackId)
        // Overlaps the id-1 box, but never jumps to it while id 2 is present.
        assertNull(matchDetection(prev, car(0.2, trackId = 2)))
    }

    @Test
    fun farJumpIsNotTheSameObject() {
        assertNull(matchDetection(listOf(car(0.0)), car(0.7)))
    }

    @Test
    fun objectHidesOverlappingMotionBox() {
        val motion = TrackDetection(label = "motion", box = TrackBox(0.1, 0.2, 0.2, 0.2))
        val farMotion = TrackDetection(label = "motion", box = TrackBox(0.7, 0.7, 0.1, 0.1))
        val kept = filterObjectPriority(listOf(car(0.1), motion, farMotion))
        assertEquals(listOf(car(0.1), farMotion), kept)
    }

    @Test
    fun pixelBoxesAreNormalizedByFrameSize() {
        val box = normalizeBox(TrackBox(192.0, 108.0, 384.0, 216.0), 1920, 1080)!!
        assertEquals(0.1, box.x, 1e-9)
        assertEquals(0.2, box.height, 1e-9)
        assertNull(normalizeBox(TrackBox(192.0, 108.0, 384.0, 216.0), 0, 0))
    }
}
