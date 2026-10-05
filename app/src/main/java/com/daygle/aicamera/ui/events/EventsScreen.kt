package com.daygle.aicamera.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.daygle.aicamera.ui.LocalUse24Hour
import com.daygle.aicamera.ui.formatTimestamp
import com.daygle.aicamera.ui.LifecycleResumeEffect
import com.daygle.aicamera.ui.library.LoadMoreEffect
import com.daygle.aicamera.ui.library.PagingFooter
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.components.ZoomableImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    modifier: Modifier = Modifier,
    onPlayRecording: (Int) -> Unit = {},
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(value = false) }
    var snapshotEventId by remember { mutableStateOf<Int?>(null) }
    // "Ask AI" mode sends the question to the server's plain-English search
    // instead of filtering the loaded list by keyword.
    var aiMode by rememberSaveable { mutableStateOf(false) }
    var aiQuery by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    // Poll for new events incrementally while the screen is visible; stop when
    // the app is backgrounded so we don't hammer the server.
    LifecycleResumeEffect(onPause = viewModel::pausePolling, onResume = viewModel::startPolling)

    snapshotEventId?.let { id ->
        SnapshotViewerDialog(
            url = viewModel.snapshotUrl(id),
        ) { snapshotEventId = null }
    }

    if (showFilterSheet) {
        val s = state as? EventsUiState.Ready
        EventsFilterSheet(
            state = s?.data?.filter ?: EventsFilter(),
            availableModes = s?.data?.availableModes ?: emptyList(),
            availableTriggerTypes = s?.data?.availableTriggerTypes ?: emptyList(),
            availableSources = s?.data?.availableSources ?: emptyList(),
            availableObjectLabels = s?.data?.availableObjectLabels ?: emptyList(),
            availableSoundLabels = s?.data?.availableSoundLabels ?: emptyList(),
            availableAiTags = s?.data?.availableAiTags ?: emptyList(),
            faceFacets = s?.data?.faceFacets,
            cameraMap = s?.data?.cameras?.associate { it.id to it.displayName } ?: emptyMap(),
            onDismiss = { showFilterSheet = false },
            viewModel = viewModel,
        )
    }

    when (val s = state) {
        EventsUiState.Loading -> LoadingState(modifier)
        is EventsUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is EventsUiState.Ready -> {
            val data = s.data
            val activeFilterCount = data.filter.activeCount()

            Column(modifier) {
                // Search & Filter Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = if (aiMode) aiQuery else data.filter.query,
                            onValueChange = { if (aiMode) aiQuery = it else viewModel.setQuery(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (aiMode) "Ask AI: red car yesterday…" else "Search events, AI tags, faces…",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (aiMode) Icons.Filled.AutoAwesome else Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = if (aiMode) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
                                )
                            },
                            trailingIcon = {
                                val current = if (aiMode) aiQuery else data.filter.query
                                if (current.isNotBlank()) {
                                    IconButton(onClick = {
                                        if (aiMode) {
                                            aiQuery = ""
                                            viewModel.clearAiSearch()
                                        } else {
                                            viewModel.setQuery("")
                                        }
                                    }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (aiMode) viewModel.runAiSearch(aiQuery)
                                keyboard?.hide()
                            }),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            )
                        )

                        IconButton(
                            onClick = {
                                aiMode = !aiMode
                                if (!aiMode) viewModel.clearAiSearch()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (aiMode) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = if (aiMode) "Turn off Ask AI" else "Ask AI",
                                tint = if (aiMode) MaterialTheme.colorScheme.onTertiaryContainer else LocalContentColor.current,
                            )
                        }

                        BadgedBox(
                            badge = {
                                if (activeFilterCount > 0) {
                                    Badge { Text(activeFilterCount.toString()) }
                                }
                            }
                        ) {
                            IconButton(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = "Filter")
                            }
                        }
                    }
                }

                // Quick Chip Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { showFilterSheet = true },
                        label = { Text(data.filter.sortOrder.label) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    data.filter.selectedModes.forEach { mode ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.toggleMode(mode) },
                            label = { Text(mode) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if ((data.filter.dateStart != null) || (data.filter.dateEnd != null)) {
                        AssistChip(
                            onClick = { showFilterSheet = true },
                            label = { Text(dateRangeLabel(data.filter.dateStart, data.filter.dateEnd)) },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Clear,
                                    null,
                                    Modifier.size(16.dp).clickable { viewModel.setDateRange(null, null) }
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (activeFilterCount > 0) {
                        TextButton(onClick = viewModel::clearFilters) {
                            Text("Reset", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (activeFilterCount > 0 && data.aiSearch == null && !data.refreshing) {
                    Text(
                        "${data.filtered.size}${if (data.hasMore) "+" else ""} matching events",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                val aiSearch = data.aiSearch
                if (aiSearch != null) {
                    AiSearchResults(
                        search = aiSearch,
                        onRetry = { viewModel.runAiSearch(aiSearch.query) },
                        onClose = {
                            aiQuery = ""
                            viewModel.clearAiSearch()
                        },
                        onPlayRecording = onPlayRecording,
                        onOpenSnapshot = { snapshotEventId = it },
                        modifier = Modifier.weight(1f),
                    )
                } else PullToRefreshBox(
                    isRefreshing = data.refreshing,
                    onRefresh = viewModel::load,
                    modifier = Modifier.weight(1f),
                ) {
                    if (data.filtered.isEmpty() && !data.hasMore && !data.loadingMore && data.loadMoreError == null) {
                        EmptyState(
                            if (activeFilterCount > 0) "No events match your filters." else "No events recorded yet.",
                        )
                    } else {
                        val lazyListState = rememberLazyListState(
                            initialFirstVisibleItemIndex = viewModel.scrollIndex
                        )
                        LaunchedEffect(lazyListState) {
                            snapshotFlow { lazyListState.firstVisibleItemIndex }
                                .collect { index -> viewModel.saveScrollIndex(index) }
                        }
                        LoadMoreEffect(lazyListState, hasMore = data.hasMore, onLoadMore = viewModel::loadMore)
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(data.filtered, key = { it.id }) { event ->
                                EventRow(
                                    event,
                                    onPlayRecording = onPlayRecording,
                                ) { snapshotEventId = it }
                            }
                            item(key = "paging-footer") {
                                PagingFooter(
                                    loadingMore = data.loadingMore,
                                    loadMoreError = data.loadMoreError,
                                    hasMore = data.hasMore,
                                    onLoadMore = viewModel::loadMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSearchResults(
    search: AiSearchState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onPlayRecording: (Int) -> Unit,
    onOpenSnapshot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "ai-search-header") {
            AiSearchHeader(search = search, onClose = onClose)
        }
        when {
            search.loading -> item(key = "ai-search-loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            search.error != null -> item(key = "ai-search-error") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        search.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry) { Text("Try Again") }
                }
            }
            search.results.isEmpty() -> item(key = "ai-search-empty") {
                Text(
                    "No described events match. Only events the AI model has described can be found this way.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            else -> items(search.results, key = { it.id }) { event ->
                EventRow(event, onPlayRecording = onPlayRecording, onOpenSnapshot = onOpenSnapshot)
            }
        }
    }
}

/** Shows how the server understood the question, so the user can rephrase. */
@Composable
private fun AiSearchHeader(search: AiSearchState, onClose: () -> Unit) {
    val use24Hour = LocalUse24Hour.current
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp).size(18.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "\u201C${search.query}\u201D",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val interpretation = search.interpretation
                if (interpretation != null) {
                    val parts = buildList {
                        if (interpretation.terms.isNotEmpty()) {
                            val joiner = if (interpretation.relaxed) " or " else " + "
                            add("Looking for " + interpretation.terms.joinToString(joiner) { group -> group.firstOrNull().orEmpty() })
                        }
                        interpretation.camera?.let { add("on $it") }
                        if (interpretation.since != null || interpretation.until != null) {
                            add(
                                listOfNotNull(
                                    interpretation.since?.let { "from ${formatTimestamp(it, use24Hour)}" },
                                    interpretation.until?.let { "to ${formatTimestamp(it, use24Hour)}" },
                                ).joinToString(" ")
                            )
                        }
                    }
                    if (parts.isNotEmpty()) {
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val by = if (interpretation.interpretedBy == "model") "Understood by the AI model" else "Matched by keywords (AI model unavailable)"
                    val count = if (search.loading) "" else " · ${search.results.size} result${if (search.results.size == 1) "" else "s"}"
                    Text(
                        by + count,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    if (interpretation.relaxed) {
                        Text(
                            "Nothing matched every term, so these match any of them.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                } else if (search.loading) {
                    Text(
                        "Searching AI descriptions…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close AI search")
            }
        }
    }
}

@Composable
private fun SnapshotViewerDialog(url: String?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) {
                ZoomableImage(
                    model = url,
                    contentDescription = "Event snapshot",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "Snapshot unavailable",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
