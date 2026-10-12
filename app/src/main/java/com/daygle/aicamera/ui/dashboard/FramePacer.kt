package com.daygle.aicamera.ui.dashboard

import android.os.SystemClock

/**
 * Paces a polled live-frame URL so the frame being downloaded is never cut
 * off by the next poll tick.
 *
 * The dashboard hands each view a fresh cache-busted URL every refresh
 * interval. Loading it straight away makes the image loader cancel the
 * download still in flight; on a slow link (mobile data through a tunnel)
 * a full frame can take longer than the interval, so frames were dropped
 * half-way, over and over, and the tunnel logged every one as an aborted
 * request. Instead a URL offered while a frame is loading waits, and the
 * newest waiting one is loaded as soon as that frame settles (loads or
 * fails). A frame stuck for [stallMs] is given up on at the next offer.
 */
class FramePacer(
    private val stallMs: Long = FRAME_STALL_MS,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    /** The URL being loaded, or shown once it has loaded. */
    var current: String? = null
        private set
    private var loadingSince: Long? = null
    private var pending: String? = null

    /** A newly polled URL. Returns the URL to load now, or null to keep the current frame. */
    fun offer(url: String?): String? {
        if (url == null || url == current) return null
        val since = loadingSince
        if (since != null && now() - since < stallMs) {
            pending = url
            return null
        }
        return start(url)
    }

    /**
     * The frame for [url] finished loading or failed. Returns the URL to load
     * next when a newer one is waiting; a callback for an older URL is ignored.
     */
    fun settled(url: String?): String? {
        if (url == null || url != current) return null
        loadingSince = null
        val next = pending ?: return null
        return start(next)
    }

    /** Forget a waiting URL (the view was paused): the current frame stays. */
    fun dropPending() {
        pending = null
    }

    private fun start(url: String): String {
        current = url
        pending = null
        loadingSince = now()
        return url
    }

    companion object {
        const val FRAME_STALL_MS = 10_000L
    }
}
