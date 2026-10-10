package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.Recording
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server's `media_ready` flag on a recording, and on the clip an event
 * links to in `event.recordings`, holds the Play action until the file is
 * written. A payload without the flag reads as ready, as the web UI does.
 */
class RecordingReadyTest {

    private val json = SessionManager.json

    @Test
    fun recordingReadsMediaReadyFlag() {
        assertFalse(json.decodeFromString<Recording>("""{"id": 1, "media_ready": false}""").mediaReady)
        assertTrue(json.decodeFromString<Recording>("""{"id": 1, "media_ready": true}""").mediaReady)
    }

    @Test
    fun recordingWithoutFlagReadsAsReady() {
        assertTrue(json.decodeFromString<Recording>("""{"id": 1}""").mediaReady)
    }

    @Test
    fun eventWithClipBeingWrittenIsPreparing() {
        val event = json.decodeFromString<Event>(
            """{"id": 9, "recording_id": 4, "recordings": [{"id": 4, "media_ready": false}]}"""
        )
        assertEquals(4, event.playableRecordingId)
        assertTrue(event.recordingPreparing)
    }

    @Test
    fun eventWithReadyClipIsNotPreparing() {
        val event = json.decodeFromString<Event>(
            """{"id": 9, "recording_id": 4, "recordings": [{"id": 3, "media_ready": false}, {"id": 4, "media_ready": true}]}"""
        )
        assertFalse(event.recordingPreparing)
    }

    @Test
    fun eventWithoutLinkedClipDetailsIsNotPreparing() {
        val event = json.decodeFromString<Event>("""{"id": 9, "recording_id": 4}""")
        assertEquals(4, event.playableRecordingId)
        assertFalse(event.recordingPreparing)
    }

    @Test
    fun eventFallsBackToFirstLinkedRecording() {
        val event = json.decodeFromString<Event>("""{"id": 9, "recordings": [{"id": 5, "media_ready": false}]}""")
        assertEquals(5, event.playableRecordingId)
        assertTrue(event.recordingPreparing)
    }

    @Test
    fun soundEventHasNoClip() {
        val event = json.decodeFromString<Event>("""{"id": 9, "source": "sound", "recording_status": "none"}""")
        assertNull(event.playableRecordingId)
        assertFalse(event.recordingPreparing)
    }
}
