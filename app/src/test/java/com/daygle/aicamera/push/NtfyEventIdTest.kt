package com.daygle.aicamera.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyEventIdTest {

    @Test
    fun readsEventIdFromTag() {
        val message = NtfyMessage(tags = listOf("warning", "daygle-event-42"), message = "Person Detected")
        assertEquals(42, eventIdFrom(message))
    }

    @Test
    fun tagWinsOverBodyText() {
        val message = NtfyMessage(tags = listOf("daygle-event-7"), message = "Event ID: 99")
        assertEquals(7, eventIdFrom(message))
    }

    @Test
    fun fallsBackToBodyForServersWithoutTheTag() {
        assertEquals(123, eventIdFrom(NtfyMessage(message = "Camera: Front Door\nEvent ID: 123")))
    }

    @Test
    fun noEventForCameraOfflinePushes() {
        assertNull(eventIdFrom(NtfyMessage(message = "Camera Gate (gate) has gone offline.")))
        assertNull(eventIdFrom(NtfyMessage(tags = listOf("daygle-event-"))))
    }
}
