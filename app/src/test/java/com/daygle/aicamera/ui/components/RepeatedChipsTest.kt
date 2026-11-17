package com.daygle.aicamera.ui.components

import com.daygle.aicamera.data.model.AiVerdict
import com.daygle.aicamera.data.model.AiVerification
import com.daygle.aicamera.data.model.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A Verified chip that only repeats the detections' ✓ marks is left out. */
class RepeatedChipsTest {

    private val verified = AiVerification(AiVerdict.CONFIRMED, "The AI model confirmed this alert.", emptyList())

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
