package com.daygle.aicamera.ui.events

import com.daygle.aicamera.data.SessionManager
import com.daygle.aicamera.data.model.AiVerdict
import com.daygle.aicamera.data.model.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class EventsViewModelTest {

    private val json = SessionManager.json

    @Test
    fun filtersByAiCheck() {
        val events = listOf(
            """{"id": 1, "metadata": {"ai_verification": {"status": "confirmed"}}}""",
            """{"id": 2, "metadata": {"ai_verification": {"status": "filtered"}}}""",
            """{"id": 3, "metadata": {"ai_verification": {"status": "unsure"}}}""",
            """{"id": 4, "metadata": {"ai_verification": {"status": "error"}}}""",
            """{"id": 5, "metadata": {"ai_verification": {"status": "skipped"}}}""",
            """{"id": 6, "metadata": {}}""",
        ).map { json.decodeFromString<Event>(it) }

        assertEquals(6, EventsViewModel.applyLocalFilters(events, EventsFilter()).size)
        fun ids(verdict: AiVerdict) =
            EventsViewModel.applyLocalFilters(events, EventsFilter(aiCheck = verdict)).map { it.id }
        assertEquals(listOf(1), ids(AiVerdict.CONFIRMED))
        assertEquals(listOf(2), ids(AiVerdict.FILTERED))
        assertEquals(listOf(3, 4, 5), ids(AiVerdict.UNVERIFIED))
    }

    @Test
    fun aiCheckIsSentToTheServerAndCountsAsAFilter() {
        val filter = EventsFilter(aiCheck = AiVerdict.FILTERED)
        assertEquals(AiVerdict.FILTERED, filter.toQuery().aiCheck)
        assertEquals("filtered", filter.toQuery().aiCheck?.queryValue)
        assertEquals(1, filter.activeCount())
        assertEquals(null, EventsFilter().toQuery().aiCheck)
    }
}
