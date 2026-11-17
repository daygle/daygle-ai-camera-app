package com.daygle.aicamera.ui.components

import com.daygle.aicamera.data.model.AiVerdict
import com.daygle.aicamera.data.model.AiVerification
import com.daygle.aicamera.data.model.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Chips that only repeat a detection's AI mark are left out. */
class RepeatedChipsTest {

    private val verified = AiVerification(AiVerdict.CONFIRMED, "The AI model confirmed this alert.", emptyList())

    @Test
    fun reclassifiedChipIsDroppedWhenTheDetectionShowsTheMark() {
        val found = mapOf("dog" to "cat")
        assertEquals(emptyMap<String, String>(),
            unmarkedReclassified(found, listOf(Detection("cat", 0.8, aiStatus = "reclassified"))))
        // Older events have no per-detection verdict: the chip stays.
        assertEquals(found, unmarkedReclassified(found, listOf(Detection("cat", 0.8))))
        assertEquals(found, unmarkedReclassified(found, emptyList()))
    }

    @Test
    fun plainVerifiedIsDroppedOnlyWhenEveryObjectIsTicked() {
        assertNull(verdictWorthShowing(verified, listOf(
            Detection("person", 0.9, aiStatus = "confirmed"),
            Detection("cat", 0.7, aiStatus = "reclassified"),
            Detection("motion", 1.0),
        )))
        assertEquals(verified, verdictWorthShowing(verified, listOf(
            Detection("person", 0.9, aiStatus = "confirmed"),
            Detection("car", 0.6),
        )))
        assertEquals(verified, verdictWorthShowing(verified, emptyList()))
        // Filtered explains the notification, so it always shows.
        val filtered = verified.copy(verdict = AiVerdict.FILTERED)
        assertEquals(filtered, verdictWorthShowing(filtered, listOf(Detection("person", 0.5, aiStatus = "rejected"))))
        assertNull(verdictWorthShowing(null, emptyList()))
    }
}
