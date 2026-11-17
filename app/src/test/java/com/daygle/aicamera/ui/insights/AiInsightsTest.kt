package com.daygle.aicamera.ui.insights

import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.model.AiInsightsResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AiInsightsTest {

    private val json = SessionManager.json

    @Test
    fun parsesTheServersInsights() {
        val insights = json.decodeFromString<AiInsightsResponse>(
            """{"tuning": [{"camera_id": "cam-1", "camera_name": "Drive", "label": "person", "zones": ["Drive"],
                  "current_min_confidence": 0.4, "min_confidence": 0.55, "rejected_below": 9, "judged_below": 10,
                  "confirmed_lost": 1, "confirmed_total": 20, "extra": true}],
                "ignore_areas": [{"camera_id": "cam-1", "label": "dog", "zones": ["Yard"],
                  "area": {"x": 0.1, "y": 0.6, "width": 0.1, "height": 0.2}, "rejected": 8, "confirmed": 0,
                  "sample_event_id": 42}],
                "ignored_areas": [{"camera_id": "cam-1", "camera_name": "Drive", "zone_id": "yard", "zone_name": "Yard",
                  "index": 0, "label": "dog", "x": 0.1, "y": 0.6, "width": 0.1, "height": 0.2, "in_zone": false}],
                "frequent_tags": [{"camera_id": "cam-1", "camera_name": "Drive", "tags": [{"tag": "parcel", "count": 4}]}],
                "tuning_window_days": 14, "tags_window_days": 7}"""
        )
        assertEquals(0.55, insights.tuning.single().minConfidence, 0.0)
        assertEquals(42, insights.ignoreAreas.single().sampleEventId)
        assertEquals(0.6, insights.ignoredAreas.single().area.y, 0.0)
        assertFalse(insights.ignoredAreas.single().inZone)
        assertEquals("parcel", insights.frequentTags.single().tags.single().tag)
        // A suggestion without a snapshot, and an older server's area without in_zone.
        val older = json.decodeFromString<AiInsightsResponse>(
            """{"ignore_areas": [{"camera_id": "c", "label": "dog", "area": {"x": 0, "y": 0, "width": 0.1, "height": 0.1}}],
                "ignored_areas": [{"camera_id": "c", "zone_id": "z", "index": 1, "label": "dog",
                  "x": 0, "y": 0, "width": 0.1, "height": 0.1, "source": "ai"}]}"""
        )
        assertNull(older.ignoreAreas.single().sampleEventId)
        assertTrue(older.ignoredAreas.single().inZone)
    }

    @Test
    fun explainsWhyInsightsCannotLoad() {
        fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))
        assertEquals("Only administrators can see AI Insights.", loadErrorMessage(http(403)))
        assertTrue(loadErrorMessage(http(404)).contains("Update the Daygle server"))
        assertTrue(loadErrorMessage(http(500)).startsWith("Couldn't load"))
    }
}
