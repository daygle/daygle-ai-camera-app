package com.daygle.aicamera.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramePacerTest {

    private var clock = 0L
    private fun pacer() = FramePacer(stallMs = 10_000L, now = { clock })

    @Test
    fun firstUrlLoadsAtOnce() {
        val pacer = pacer()
        assertEquals("a", pacer.offer("a"))
        assertEquals("a", pacer.current)
        assertNull(pacer.offer(null))
        assertNull(pacer.offer("a"))
    }

    @Test
    fun urlsOfferedWhileLoadingWaitAndTheNewestLoadsOnSettle() {
        val pacer = pacer()
        pacer.offer("a")
        clock += 1_000
        assertNull(pacer.offer("b"))
        clock += 1_000
        assertNull(pacer.offer("c"))
        assertEquals("a", pacer.current)
        assertEquals("c", pacer.settled("a"))
        // Nothing waiting after that: the next settle starts nothing.
        assertNull(pacer.settled("c"))
        assertEquals("d", pacer.offer("d"))
    }

    @Test
    fun settledFrameLetsTheNextOfferLoadAtOnce() {
        val pacer = pacer()
        pacer.offer("a")
        assertNull(pacer.settled("a"))
        assertEquals("b", pacer.offer("b"))
    }

    @Test
    fun staleSettleCallbacksAreIgnored() {
        val pacer = pacer()
        pacer.offer("a")
        pacer.offer("b")
        assertNull(pacer.settled("old"))
        assertEquals("a", pacer.current)
    }

    @Test
    fun stalledFrameIsReplacedAtTheNextOffer() {
        val pacer = pacer()
        pacer.offer("a")
        clock += 9_999
        assertNull(pacer.offer("b"))
        clock += 1
        assertEquals("c", pacer.offer("c"))
    }

    @Test
    fun droppedPendingIsNotLoaded() {
        val pacer = pacer()
        pacer.offer("a")
        pacer.offer("b")
        pacer.dropPending()
        assertNull(pacer.settled("a"))
        assertEquals("a", pacer.current)
    }
}
