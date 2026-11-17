package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.FalsePositiveFilter
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.misidentified
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
    fun misreadObjectsAreMisidentifiedNotFalsePositives() {
        val event = json.decodeFromString<Event>(
            """{"id": 7, "false_positive": 0, "false_positive_by": "ai",
               "metadata": {"ai_false_positive_check": {"status": "misidentified", "misidentified": {"dog": "cat"}}}}"""
        )
        assertNull(event.falsePositive)
        assertEquals(mapOf("dog" to "cat"), event.misidentified())
        // A user's false-positive mark wins over the AI's relabel.
        val marked = json.decodeFromString<Event>(
            """{"id": 8, "false_positive": 1, "false_positive_by": "user",
               "metadata": {"ai_false_positive_check": {"misidentified": {"dog": "cat"}}}}"""
        )
        assertTrue(marked.misidentified().isEmpty())
        assertTrue(json.decodeFromString<Event>("""{"id": 9}""").misidentified().isEmpty())
        val recording = json.decodeFromString<Recording>(
            """{"id": 3, "events": [{"id": 7, "false_positive": 0,
               "metadata": {"ai_false_positive_check": {"misidentified": {"dog": "cat"}}}}, {"id": 10}]}"""
        )
        assertEquals(mapOf("dog" to "cat"), recording.misidentified())
        assertEquals("Looks like a Cat", com.daygle.aicamera.ui.components.misidentifiedText("cat"))
        assertEquals("Looks like an Owl", com.daygle.aicamera.ui.components.misidentifiedText("owl"))
        assertEquals("Another Animal", com.daygle.aicamera.ui.components.misidentifiedText("another animal"))
    }
}
