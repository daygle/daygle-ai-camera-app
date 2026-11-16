package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.AiVerdict
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.EventSearchResponse
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.TimelineResponse
import com.daygle.aicamera.data.model.aiDescription
import com.daygle.aicamera.data.model.aiTags
import com.daygle.aicamera.data.model.aiVerdict
import com.daygle.aicamera.data.model.aiVerification
import com.daygle.aicamera.data.model.faceIdentities
import com.daygle.aicamera.data.model.motionFraction
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInsightsTest {

    private val json = SessionManager.json

    private val describedEvent = json.decodeFromString<Event>(
        """
        {"id": 42, "source": "front", "detections": [
            {"label": "person", "confidence": 0.91, "zone_name": "Driveway"},
            {"label": "motion", "confidence": 1.0, "motion_fraction": 0.034}
         ],
         "metadata": {
            "camera_name": "Front Door",
            "ai_description": {"text": "A courier carries a parcel to the door.", "tags": ["Parcel", "hi-vis vest", " "]},
            "ai_verification": {"status": "confirmed", "labels": {}},
            "face_identities": {"people": [{"person_id": 3, "name": "Alice"}, {"person_id": 3, "name": "Alice"}], "unknown": 1}
         }}
        """
    )

    @Test
    fun parsesDescriptionAndTags() {
        val description = describedEvent.aiDescription()!!
        assertEquals("A courier carries a parcel to the door.", description.text)
        assertEquals(listOf("parcel", "hi-vis vest"), description.tags)
    }

    @Test
    fun undescribedEventHasNoDescription() {
        val event = json.decodeFromString<Event>("""{"id": 1, "metadata": {"label": "person"}}""")
        assertNull(event.aiDescription())
        assertNull(event.aiVerdict())
        assertTrue(event.faceIdentities().isEmpty)
    }

    @Test
    fun parsesVerdictFacesAndMotionShare() {
        assertEquals(AiVerdict.CONFIRMED, describedEvent.aiVerdict())
        val faces = describedEvent.faceIdentities()
        assertEquals(listOf("Alice"), faces.people.map { it.name })
        assertEquals("id:3", faces.people.single().key)
        assertEquals(1, faces.unknown)
        assertEquals(0.034, describedEvent.detections.motionFraction()!!, 1e-9)
    }

    @Test
    fun recordingTagsPreferServerAiLabels() {
        val recording = json.decodeFromString<Recording>(
            """{"id": 9, "labels": ["person"], "ai_labels": ["ladder"],
                "event": {"id": 42, "metadata": {"ai_description": {"text": "x", "tags": ["parcel"]}}}}"""
        )
        assertEquals(listOf("ladder"), recording.aiTags())

        val older = json.decodeFromString<Recording>(
            """{"id": 9, "events": [{"id": 42, "metadata": {"ai_description": {"tags": ["parcel"]}}}]}"""
        )
        assertEquals(listOf("parcel"), older.aiTags())
    }

    @Test
    fun decodesSearchResponseAndTimelineAlertFlag() {
        val response = json.decodeFromString<EventSearchResponse>(
            """{"items": [{"id": 5}], "interpretation": {"terms": [["car", "vehicle"]], "camera": "Driveway",
                "since": "2026-10-04T02:00:00+00:00", "until": null, "interpreted_by": "model", "relaxed": false}}"""
        )
        assertEquals(listOf(5), response.items.map { it.id })
        assertEquals("model", response.interpretation?.interpretedBy)
        assertEquals(listOf(listOf("car", "vehicle")), response.interpretation?.terms)

        val timeline = json.decodeFromString<TimelineResponse>(
            """{"recordings": [{"id": 1, "alerted": true}, {"id": 2}]}"""
        )
        assertEquals(listOf(true, false), timeline.recordings.map { it.alerted })
    }

    @Test
    fun verificationCarriesSummaryAndReasons() {
        val filtered = json.decodeFromString<Event>(
            """{"id": 7, "metadata": {"ai_verification": {"status": "filtered", "delivered": false,
               "labels": {"person": {"present": false, "reason": "a coat on a hook", "instead": "owl"}}}}}"""
        ).aiVerification()!!
        assertEquals(AiVerdict.FILTERED, filtered.verdict)
        assertTrue(filtered.summary.startsWith("Notification not sent"))
        assertEquals(listOf("person: not present - a coat on a hook; looks like an owl"), filtered.details)

        val partial = json.decodeFromString<Event>(
            """{"id": 8, "metadata": {"ai_verification": {"status": "filtered", "delivered": true, "labels": {}}}}"""
        ).aiVerification()!!
        assertTrue(partial.summary.contains("the rest was still sent"))
    }

    @Test
    fun unsureErrorAndSkippedAreUnverified() {
        val unsure = json.decodeFromString<Event>(
            """{"id": 9, "metadata": {"ai_verification": {"status": "unsure",
               "labels": {"fire_hydrant": {"present": null, "unsure": true, "reason": "too dark"}}}}}"""
        ).aiVerification()!!
        assertEquals(AiVerdict.UNVERIFIED, unsure.verdict)
        assertEquals(listOf("fire hydrant: unsure - too dark"), unsure.details)

        val error = json.decodeFromString<Event>(
            """{"id": 10, "metadata": {"ai_verification": {"status": "error",
               "labels": {"person": {"present": null, "error": "model did not answer within 20s"}}}}}"""
        ).aiVerification()!!
        assertEquals(AiVerdict.UNVERIFIED, error.verdict)
        assertEquals(listOf("person: error (model did not answer within 20s)"), error.details)

        val skipped = json.decodeFromString<Event>(
            """{"id": 11, "metadata": {"ai_verification": {"status": "skipped",
               "reason": "Verification queue backlog; delivered unverified."}}}"""
        ).aiVerification()!!
        assertEquals(AiVerdict.UNVERIFIED, skipped.verdict)
        assertEquals("Sent without AI verification: Verification queue backlog; delivered unverified.", skipped.summary)

        val unknown = json.decodeFromString<Event>(
            """{"id": 12, "metadata": {"ai_verification": {"status": "something-new"}}}"""
        )
        assertNull(unknown.aiVerification())
    }
}
