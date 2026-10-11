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
        // Overlaps the id-1 box and not the id-2 one, but the tracker id decides.
        assertEquals(2, matchDetection(prev, car(0.2, trackId = 2))!!.trackId)
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

    @Test
    fun clipWithoutTrackFallsBackToEventBoxesFromTheTrigger() {
        val recording = SessionManager.json.decodeFromString<Recording>(
            """{"id": 1, "started_at": "2026-10-11T21:05:00+00:00",
                "event": {"id": 9, "created_at": "2026-10-11T21:05:15+00:00"},
                "detections": [
                  {"label": "car", "confidence": 0.9, "x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4},
                  {"label": "motion", "confidence": 1.0, "x": 0.6, "y": 0.6, "width": 0.1, "height": 0.1}]}"""
        )
        val source = overlaySource(recording)!!
        assertEquals(15.0, source.eventFrom, 1e-9)
        // Nothing over the pre-roll, then the specific object only.
        assertTrue(source.detectionsAt(10.0).isEmpty())
        val shown = source.detectionsAt(20.0).single()
        assertEquals("car", shown.label)
        assertEquals(0.3, shown.box!!.width, 1e-9)
    }

    @Test
    fun genericEventBoxesShowWhenNothingSpecificWasDetected() {
        val recording = SessionManager.json.decodeFromString<Recording>(
            """{"id": 1, "detections": [{"label": "motion", "x": 0.6, "y": 0.6, "width": 0.1, "height": 0.1}]}"""
        )
        assertEquals("motion", overlaySource(recording)!!.detectionsAt(0.0).single().label)
    }

    @Test
    fun trackWinsOverEventBoxesAndNoBoxesMeansNoOverlay() {
        val withTrack = SessionManager.json.decodeFromString<Recording>(
            """{"id": 1, "track": [{"t": 1, "detections": [{"label": "dog",
                 "box": {"x": 0.1, "y": 0.1, "width": 0.2, "height": 0.2}}]}],
                "detections": [{"label": "car", "x": 0.5, "y": 0.5, "width": 0.1, "height": 0.1}]}"""
        )
        assertEquals("dog", overlaySource(withTrack)!!.detectionsAt(1.0).single().label)
        assertNull(overlaySource(SessionManager.json.decodeFromString<Recording>("""{"id": 1}""")))
        assertNull(
            overlaySource(SessionManager.json.decodeFromString<Recording>("""{"id": 1, "detections": [{"label": "car"}]}"""))
        )
    }
}
