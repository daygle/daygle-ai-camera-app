package com.daygle.aicamera.ui.library

import com.daygle.aicamera.data.model.Page
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

class LibraryPagerTest {

    // Unconfined runs each launch inline, so fetches that never suspend
    // complete before the call returns.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun pager() = LibraryPager<Int>(scope, idOf = { it }) {}

    /** Pages of 3 ids counting down from 100; the cursor is the next start. */
    private val pages: suspend (String?) -> Result<Page<Int>> = { cursor ->
        val start = cursor?.toInt() ?: 100
        Result.success(Page(items = (start downTo start - 2).toList(), nextCursor = (start - 3).toString().takeIf { start > 94 }))
    }

    @Test
    fun reloadThenLoadMoreAppendsUntilTheLastPage() {
        val pager = pager()
        pager.reload(pages)
        assertEquals(listOf(100, 99, 98), pager.snapshot.items)
        assertFalse(pager.snapshot.initialLoading)
        assertTrue(pager.snapshot.hasMore)

        pager.loadMore()
        pager.loadMore()
        assertEquals((100 downTo 92).toList(), pager.snapshot.items)
        assertFalse(pager.snapshot.hasMore)

        pager.loadMore() // no cursor: a no-op
        assertEquals(9, pager.snapshot.items.size)
    }

    @Test
    fun replaceSwapsLoadedRowsInPlaceAndIgnoresUnknownOnes() {
        val pager = LibraryPager<Pair<Int, String>>(scope, idOf = { it.first }) {}
        pager.reload({ Result.success(Page(listOf(1 to "old", 2 to "old", 3 to "old"))) })
        pager.replace(listOf(2 to "new", 9 to "new"))
        assertEquals(listOf(1 to "old", 2 to "new", 3 to "old"), pager.snapshot.items)
    }

    @Test
    fun refreshStaleSwapsInFreshCopiesOfStaleRowsOnly() {
        val pager = LibraryPager<Pair<Int, String>>(scope, idOf = { it.first }) {}
        pager.reload({ Result.success(Page(listOf(1 to "ready", 2 to "preparing", 3 to "preparing"))) })
        val fetched = mutableListOf<Int>()
        pager.refreshStale(
            isStale = { it.second == "preparing" },
            fetchOne = { id -> fetched += id; if (id == 3) Result.failure(IOException()) else Result.success(id to "ready") },
            delayMs = 0,
        )
        assertEquals(listOf(2, 3), fetched)
        // A failed fetch keeps the old row.
        assertEquals(listOf(1 to "ready", 2 to "ready", 3 to "preparing"), pager.snapshot.items)
    }

    @Test
    fun refreshStaleGivesUpOnARowThatNeverBecomesReady() {
        val pager = LibraryPager<Pair<Int, String>>(scope, idOf = { it.first }) {}
        pager.reload({ Result.success(Page(listOf(1 to "preparing"))) })
        var fetches = 0
        repeat(LibraryPager.MAX_STALE_CHECKS + 5) {
            pager.refreshStale(
                isStale = { it.second == "preparing" },
                fetchOne = { id -> fetches++; Result.success(id to "preparing") },
                delayMs = 0,
            )
        }
        assertEquals(LibraryPager.MAX_STALE_CHECKS, fetches)
    }

    @Test
    fun aSlowOldQueryCannotOverwriteTheNewOne() {
        val pager = pager()
        val slow = CompletableDeferred<Result<Page<Int>>>()
        pager.reload({ slow.await() })
        pager.reload({ Result.success(Page(listOf(1, 2))) })
        slow.complete(Result.success(Page(listOf(7, 8, 9))))
        assertEquals(listOf(1, 2), pager.snapshot.items)
    }

    @Test
    fun aFailedLaterPageKeepsLoadedRows() {
        val pager = pager()
        var fail = false
        pager.reload({ cursor ->
            if (fail) Result.failure(IOException("offline")) else pages(cursor)
        })
        fail = true
        pager.loadMore()
        assertEquals(listOf(100, 99, 98), pager.snapshot.items)
        assertNotNull(pager.snapshot.loadMoreError)
        assertFalse(pager.snapshot.loadingMore)

        fail = false
        pager.loadMore()
        assertNull(pager.snapshot.loadMoreError)
        assertEquals(6, pager.snapshot.items.size)
    }

    @Test
    fun aFailedFirstPageIsAnError() {
        val pager = pager()
        pager.reload({ Result.failure(IOException("offline")) })
        assertNotNull(pager.snapshot.error)
        assertTrue(pager.snapshot.items.isEmpty())
    }

    @Test
    fun fillToFetchesALimitedNumberOfExtraPages() {
        val pager = LibraryPager<Int>(scope, idOf = { it }) {}
        var calls = 0
        pager.reload({ cursor ->
            calls++
            val start = cursor?.toInt() ?: 1000
            Result.success(Page(listOf(start), nextCursor = (start - 1).toString()))
        })
        repeat(10) { pager.fillTo(visibleCount = 0) }
        assertEquals(1 + LibraryPager.MAX_AUTO_PAGES, calls)

        // Enough visible rows: nothing more is fetched.
        pager.reload({ Result.success(Page(listOf(1), nextCursor = "x")) })
        pager.fillTo(visibleCount = LibraryPager.MIN_VISIBLE)
        assertEquals(listOf(1), pager.snapshot.items)
    }

    @Test
    fun pollHeadPrependsOnlyNewRows() {
        val pager = pager()
        var head = listOf(100, 99, 98)
        pager.reload({ Result.success(Page(head)) })
        head = listOf(102, 101, 100)
        pager.pollHead()
        assertEquals(listOf(102, 101, 100, 99, 98), pager.snapshot.items)
    }

    @Test
    fun dayBoundsAreSentAsUtc() {
        val sydney = ZoneId.of("Australia/Sydney") // UTC+11 after the October DST change
        val day = LocalDate.of(2026, 10, 5)
        assertEquals("2026-10-04T13:00:00.000000+00:00", startOfDayIso(day, sydney))
        assertEquals("2026-10-05T12:59:59.999999+00:00", endOfDayIso(day, sydney))
        assertNull(startOfDayIso(null))
        assertNull(endOfDayIso(null))
    }
}
