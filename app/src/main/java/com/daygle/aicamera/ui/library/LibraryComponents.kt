package com.daygle.aicamera.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daygle.aicamera.data.model.FaceFacets

/**
 * Asks for the next page when the list is scrolled within [threshold] rows of
 * its end. [onLoadMore] is expected to ignore calls while a page is loading.
 */
@Composable
fun LoadMoreEffect(
    listState: LazyListState,
    hasMore: Boolean,
    threshold: Int = 8,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - threshold
        }
    }
    // Keyed on both, so a short list that never scrolls still asks again when
    // another page becomes available (e.g. after a filter change).
    LaunchedEffect(hasMore, nearEnd) {
        if (hasMore && nearEnd) currentOnLoadMore()
    }
}

/** Last row of a paged list: a spinner while loading, or a retry after a failure. */
@Composable
fun PagingFooter(
    loadingMore: Boolean,
    loadMoreError: String?,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loadingMore -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    loadMoreError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onLoadMore) { Text("Retry") }
            }
            hasMore -> TextButton(onClick = onLoadMore) { Text("Load More") }
        }
    }
}

/**
 * Face filter chips (single choice): any face, unrecognised faces, or one
 * recognised person, from the server's facets for the current window.
 */
@Composable
fun FaceFilterChips(
    faces: FaceFacets,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val options = buildList {
            add("any" to "Any Face")
            if (faces.unknown > 0) add("unknown" to "Unknown (${faces.unknown})")
            faces.people.forEach { add(it.value to "${it.name} (${it.count})") }
        }
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(if (selected == value) null else value) },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}
