package com.daygle.aicamera.ui.library

import com.daygle.aicamera.data.model.Page
import com.daygle.aicamera.ui.friendlyMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Snapshot of a [LibraryPager]: the rows loaded so far and the paging status. */
data class PagerSnapshot<T>(
    val items: List<T> = emptyList(),
    /** True until the first page of the current query arrives. */
    val initialLoading: Boolean = true,
    /** Pull-to-refresh (or a filter change) is reloading the first page. */
    val refreshing: Boolean = false,
    /** First-page failure; shown in place of the list. */
    val error: String? = null,
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    /** A later page failed; the loaded rows stay and the user can retry. */
    val loadMoreError: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
}

/**
 * Cursor pagination over one server-filtered list (`/api/events`,
 * `/api/snapshots`, `/api/recordings`). [reload] starts over for a new query,
 * [loadMore] appends the next page as the user scrolls, and [pollHead]
 * prepends rows that arrived since the first page loaded.
 *
 * Every change is reported through [onChange]; a newer [reload] cancels any
 * request still in flight, so a slow response for an old query can never
 * overwrite the current one.
 */
class LibraryPager<T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> Int,
    private val onChange: (PagerSnapshot<T>) -> Unit,
) {
    var snapshot: PagerSnapshot<T> = PagerSnapshot()
        private set

    private var fetch: (suspend (String?) -> Result<Page<T>>)? = null
    private var reloadJob: Job? = null
    private var moreJob: Job? = null

    /** Pages fetched automatically to fill a list thinned by client-side filters. */
    private var autoPages = 0

    private fun publish(next: PagerSnapshot<T>) {
        snapshot = next
        onChange(next)
    }

    /**
     * Load the first page of a new query. With [keepItems] the current rows
     * stay visible behind a refresh indicator instead of a full-screen spinner.
     */
    fun reload(fetch: suspend (cursor: String?) -> Result<Page<T>>, keepItems: Boolean = true) {
        this.fetch = fetch
        reloadJob?.cancel()
        moreJob?.cancel()
        autoPages = 0
        val showRows = keepItems && !snapshot.initialLoading && snapshot.error == null
        publish(
            if (showRows) snapshot.copy(refreshing = true, loadingMore = false, loadMoreError = null)
            else PagerSnapshot(initialLoading = true)
        )
        reloadJob = scope.launch {
            fetch(null).fold(
                onSuccess = { page ->
                    publish(
                        PagerSnapshot(
                            items = page.items.distinctBy(idOf),
                            initialLoading = false,
                            nextCursor = page.nextCursor,
                        )
                    )
                },
                onFailure = { error ->
                    publish(
                        if (showRows) snapshot.copy(refreshing = false, loadMoreError = error.friendlyMessage())
                        else PagerSnapshot(initialLoading = false, error = error.friendlyMessage())
                    )
                },
            )
        }
    }

    /** Append the next page, if there is one and none is loading. */
    fun loadMore() {
        val cursor = snapshot.nextCursor ?: return
        val fetch = fetch ?: return
        if (snapshot.loadingMore || reloadJob?.isActive == true) return
        publish(snapshot.copy(loadingMore = true, loadMoreError = null))
        moreJob = scope.launch {
            fetch(cursor).fold(
                onSuccess = { page ->
                    val known = snapshot.items.mapTo(HashSet(), idOf)
                    publish(
                        snapshot.copy(
                            items = snapshot.items + page.items.filter { idOf(it) !in known },
                            nextCursor = page.nextCursor,
                            loadingMore = false,
                        )
                    )
                },
                onFailure = { error ->
                    publish(snapshot.copy(loadingMore = false, loadMoreError = error.friendlyMessage()))
                },
            )
        }
    }

    /**
     * Keep a list that client-side filters thin out from looking empty: while
     * fewer than [minVisible] rows pass those filters, fetch further pages, up
     * to [MAX_AUTO_PAGES] per query so a filter matching nothing cannot walk
     * the whole history.
     */
    fun fillTo(visibleCount: Int, minVisible: Int = MIN_VISIBLE) {
        if (visibleCount >= minVisible || !snapshot.hasMore || snapshot.loadingMore) return
        if (snapshot.loadMoreError != null || autoPages >= MAX_AUTO_PAGES) return
        autoPages++
        loadMore()
    }

    /**
     * Fetch the first page again and prepend rows not loaded yet. Only
     * meaningful for newest-first lists; failures are ignored (the next poll
     * retries).
     */
    fun pollHead() {
        val fetch = fetch ?: return
        if (snapshot.initialLoading || reloadJob?.isActive == true) return
        reloadJob = scope.launch {
            val page = fetch(null).getOrNull() ?: return@launch
            val known = snapshot.items.mapTo(HashSet(), idOf)
            val added = page.items.filter { idOf(it) !in known }
            if (added.isNotEmpty()) publish(snapshot.copy(items = added + snapshot.items))
        }
    }

    companion object {
        const val MIN_VISIBLE = 20
        const val MAX_AUTO_PAGES = 5
    }
}

/** Canonical UTC `+00:00` form the server's `_normalize_iso_to_utc` stores. */
private val UTC_ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx")

/** Start of [date] in the device's zone, as the UTC ISO timestamp the server expects. */
fun startOfDayIso(date: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): String? =
    date?.atStartOfDay(zone)?.withZoneSameInstant(ZoneOffset.UTC)?.format(UTC_ISO)

/** Last instant of [date] in the device's zone (the server's `until` is inclusive). */
fun endOfDayIso(date: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): String? =
    date?.plusDays(1)?.atStartOfDay(zone)?.minusNanos(1_000)?.withZoneSameInstant(ZoneOffset.UTC)?.format(UTC_ISO)
