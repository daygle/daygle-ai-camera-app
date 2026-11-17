package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.FalsePositiveFilter
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.reclassified
import com.daygle.aicamera.ui.events.EventsFilter
import com.daygle.aicamera.ui.library.falsePositiveErrorMessage
import com.daygle.aicamera.ui.recordings.RecordingsFilter
import com.daygle.aicamera.ui.snapshots.SnapshotsFilter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class FalsePositiveTest {

    private val json = SessionManager.json

    @Test
    fun eventReadsTheServersFalsePositiveColumns() {
        val marked = json.decodeFromString<Event>(
            """{"id": 1, "false_positive": 1, "false_positive_by": "ai", "false_positive_reason": "person: a coat"}"""
        )
        assertEquals("ai", marked.falsePositive?.by)
        assertEquals("AI", marked.falsePositive?.source)
        assertEquals("person: a coat", marked.falsePositive?.reason)

        // 0 is a real detection, null undecided, and older servers send nothing.
        assertNull(json.decodeFromString<Event>("""{"id": 2, "false_positive": 0}""").falsePositive)
        assertNull(json.decodeFromString<Event>("""{"id": 3, "false_positive": null}""").falsePositive)
        assertNull(json.decodeFromString<Event>("""{"id": 4}""").falsePositive)
        // A mark without a source reads as the user's.
        assertEquals("User", json.decodeFromString<Event>("""{"id": 5, "false_positive": 1}""").falsePositive?.source)
    }

    @Test
    fun recordingReadsItsComputedStatus() {
        val recording = json.decodeFromString<Recording>(
            """{"id": 9, "false_positive": {"by": "mixed", "reason": "dog: a bush"},
               "events": [{"id": 1, "false_positive": 1, "false_positive_by": "user"}]}"""
        )
        assertEquals("AI + User", recording.falsePositive?.source)
        assertEquals("dog: a bush", recording.falsePositive?.reason)
        assertEquals("user", recording.events.single().falsePositive?.by)
        assertNull(json.decodeFromString<Recording>("""{"id": 10, "false_positive": null}""").falsePositive)
    }

    @Test
    fun filterIsSentToTheServerAndCountsOnEveryList() {
        val events = EventsFilter(falsePositive = FalsePositiveFilter.HIDE)
        assertEquals("hide", events.toQuery().falsePositive?.queryValue)
        assertEquals(1, events.activeCount())
        val recordings = RecordingsFilter(falsePositive = FalsePositiveFilter.ONLY)
        assertEquals("only", recordings.toQuery().falsePositive?.queryValue)
        assertEquals(1, recordings.copy(dateStart = null, dateEnd = null).activeCount())
        assertEquals(FalsePositiveFilter.ONLY, SnapshotsFilter(falsePositive = FalsePositiveFilter.ONLY).toQuery().falsePositive)
        assertNull(EventsFilter().toQuery().falsePositive)
    }

    @Test
    fun markBodyCarriesTheDecision() {
        assertEquals("true", falsePositiveBody(true)["false_positive"]?.jsonPrimitive?.content)
        assertEquals("false", falsePositiveBody(false)["false_positive"]?.jsonPrimitive?.content)
    }

    @Test
    fun errorsExplainOldServersAndEmptyRecordings() {
        fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))
        assertTrue(falsePositiveErrorMessage(http(404)).contains("Update the Daygle server"))
        assertTrue(falsePositiveErrorMessage(http(405)).contains("Update the Daygle server"))
        assertEquals("This recording has no detections to mark.", falsePositiveErrorMessage(http(409)))
        assertTrue(falsePositiveErrorMessage(http(500)).startsWith("Couldn't update:"))
    }

    @Test
    fun misreadObjectsAreReclassifiedNotFalsePositives() {
        val event = json.decodeFromString<Event>(
            """{"id": 7, "false_positive": 0, "false_positive_by": "ai", "detections": [{"label": "cat"}],
               "metadata": {"ai_false_positive_check": {"status": "reclassified", "reclassified": {"dog": "cat"}}}}"""
        )
        assertNull(event.falsePositive)
        assertEquals(mapOf("dog" to "cat"), event.reclassified())
        // A user's false-positive mark wins over the AI's correction.
        val marked = json.decodeFromString<Event>(
            """{"id": 8, "false_positive": 1, "false_positive_by": "user",
               "metadata": {"ai_false_positive_check": {"reclassified": {"dog": "cat"}}}}"""
        )
        assertTrue(marked.reclassified().isEmpty())
        assertTrue(json.decodeFromString<Event>("""{"id": 9}""").reclassified().isEmpty())
        val recording = json.decodeFromString<Recording>(
            """{"id": 3, "events": [{"id": 7, "false_positive": 0,
               "metadata": {"ai_false_positive_check": {"reclassified": {"dog": "cat"}}}}, {"id": 10}]}"""
        )
        assertEquals(mapOf("dog" to "cat"), recording.reclassified())
        assertEquals("Reclassified from Dog", com.daygle.aicamera.ui.components.reclassifiedText("dog"))
    }

    @Test
    fun detectionsShowTheAisVerdictAndTheObjectFilterIsSentAsAiCheck() {
        val detections = json.decodeFromString<Event>(
            """{"id": 1, "detections": [
                {"label": "person", "confidence": 0.71, "ai_status": "confirmed", "ai_reason": "a person"},
                {"label": "cat", "confidence": 0.55, "ai_status": "reclassified", "original_label": "dog"},
                {"label": "car", "confidence": 0.66, "ai_status": "rejected"},
                {"label": "truck", "confidence": 0.5}]}"""
        ).detections
        assertEquals("dog", detections[1].originalLabel)
        assertEquals(
            "Person (71%) ✓, Cat (55%) 🔄, Car (66%) ✗, Truck (50%)",
            com.daygle.aicamera.ui.formatDetectionSummary(detections),
        )
        val filter = EventsFilter(objectCheck = com.daygle.aicamera.data.model.DetectionVerdictFilter.CONFIRMED)
        assertEquals("object_confirmed", filter.toQuery().objectCheck?.queryValue)
        assertEquals(1, filter.activeCount())
    }
}
