package com.daygle.aicamera.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentIdsTest {

    @Test
    fun rejectsAnIdSeenBefore() {
        val ids = RecentIds(capacity = 10)
        assertTrue(ids.add("a"))
        assertTrue(ids.add("b"))
        assertFalse(ids.add("a"))
    }

    @Test
    fun treatsMissingIdsAsNew() {
        val ids = RecentIds(capacity = 10)
        assertTrue(ids.add(null))
        assertTrue(ids.add(null))
        assertTrue(ids.add(" "))
    }

    @Test
    fun forgetsTheOldestIdPastCapacity() {
        val ids = RecentIds(capacity = 2)
        ids.add("a")
        ids.add("b")
        ids.add("c")
        assertTrue(ids.add("a"))
        assertFalse(ids.add("c"))
    }
}
