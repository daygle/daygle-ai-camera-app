package com.daygle.aicamera.data

import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.Page
import com.daygle.aicamera.data.model.Recording
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageTest {

    private val json = SessionManager.json

    @Test
    fun decodesCursorEnvelope() {
        val page = json.decodeFromString<Page<Event>>(
            """{"items": [{"id": 7, "has_snapshot": true}, {"id": 6}], "next_cursor": "abc"}"""
        )
        assertEquals(listOf(7, 6), page.items.map { it.id })
        assertEquals(true, page.items.first().hasSnapshot)
        assertEquals("abc", page.nextCursor)
    }

    @Test
    fun lastPageHasNoCursor() {
        val page = json.decodeFromString<Page<Recording>>("""{"items": [{"id": 1}], "next_cursor": null}""")
        assertEquals(listOf(1), page.items.map { it.id })
        assertNull(page.nextCursor)
    }

    @Test
    fun decodesLegacyBareArray() {
        val page = json.decodeFromString<Page<Event>>("""[{"id": 3}, {"id": 2}]""")
        assertEquals(listOf(3, 2), page.items.map { it.id })
        assertNull(page.nextCursor)
    }

    @Test
    fun roundTripsThroughEncoding() {
        val page = Page(items = listOf(Recording(id = 5)), nextCursor = "next")
        val decoded = json.decodeFromString<Page<Recording>>(json.encodeToString(Page.serializer(Recording.serializer()), page))
        assertEquals(page, decoded)
    }
}
