package com.daygle.aicamera.ui.events

import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.model.AiVerdict
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.aiVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class EventsViewModelTest {

    private val json = SessionManager.json

    @Test
    fun filtersByAiValidatedOnly() {
        val confirmedEvent = json.decodeFromString<Event>(
            """{"id": 1, "metadata": {"ai_verification": {"status": "confirmed"}}}"""
        )
        val filteredEvent = json.decodeFromString<Event>(
            """{"id": 2, "metadata": {"ai_verification": {"status": "filtered"}}}"""
        )
        val unverifiedEvent = json.decodeFromString<Event>(
            """{"id": 3, "metadata": {}}"""
        )

        val events = listOf(confirmedEvent, filteredEvent, unverifiedEvent)

        val filterOff = EventsFilter(aiValidatedOnly = false)
        assertEquals(3, EventsViewModel.applyLocalFilters(events, filterOff).size)

        val filterOn = EventsFilter(aiValidatedOnly = true)
        val result = EventsViewModel.applyLocalFilters(events, filterOn)
        assertEquals(1, result.size)
        assertEquals(1, result.single().id)
        assertEquals(AiVerdict.CONFIRMED, result.single().aiVerdict())
    }
}
