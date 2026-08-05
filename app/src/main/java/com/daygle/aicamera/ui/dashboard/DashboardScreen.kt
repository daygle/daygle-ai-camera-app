package com.daygle.aicamera.ui.dashboard

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.daygle.aicamera.ui.LifecycleResumeEffect
import com.daygle.aicamera.ui.components.EmptyState
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keep polling while visible; stop while backgrounded or covered by another screen.
    LifecycleResumeEffect(onPause = viewModel::pause, onResume = viewModel::resume)

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    // The camera currently expanded into the full-screen live view (null = grid).
    var selectedCameraId by remember { mutableStateOf<String?>(null) }

    when (val s = state) {
        DashboardUiState.Loading -> LoadingState(modifier)
        is DashboardUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is DashboardUiState.Ready -> {
            if (s.cameras.isEmpty()) {
                EmptyState("No cameras are configured on this server yet.", modifier)
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.cameras, key = { it.camera.id }) { card ->
                        CameraTile(
                            card = card,
                            snapshotUrl = s.snapshotUrls[card.camera.id],
                            onClick = { if (card.online) selectedCameraId = card.camera.id },
                        )
                    }
                }
            }

            // Full-screen live view lives on this page: tapping a tile expands the
            // same feed with pinch-to-zoom instead of navigating to a separate screen.
            val selectedCard = selectedCameraId?.let { id -> s.cameras.find { it.camera.id == id } }
            if (selectedCard != null) {
                FullscreenCameraView(
                    card = selectedCard,
                    snapshotUrl = s.snapshotUrls[selectedCard.camera.id],
                    onClose = { selectedCameraId = null },
                )
            }
        }
    }
}

@Composable
private fun CameraTile(card: CameraCard, snapshotUrl: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (card.online) {
                    LiveThumbnail(snapshotUrl)
                } else {
                    Icon(
                        Icons.Filled.VideocamOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            ListItem(
                headlineContent = {
                    Text(
                        card.camera.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    if (card.online) {
                        LiveBadge()
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

/**
 * A self-refreshing snapshot. Each new [snapshotUrl] (cache-busted every poll
 * tick) triggers a fresh Coil request; the last successful painter is kept so
 * the tile doesn't flash a spinner between frames.
 */
@Composable
private fun LiveThumbnail(snapshotUrl: String?) {
    if (snapshotUrl == null) {
        CircularProgressIndicator(color = Color.White)
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var lastSuccessPainter by remember { mutableStateOf<Painter?>(null) }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(snapshotUrl)
            .crossfade(true)
            .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
            .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
            .build(),
        contentDescription = "Live camera view",
        onState = { coilState ->
            if (coilState is AsyncImagePainter.State.Success) {
                lastSuccessPainter = coilState.painter
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        val painterState by painter.state.collectAsStateWithLifecycle()
        val displayPainter = if (painterState is AsyncImagePainter.State.Success) {
            painterState.painter
        } else {
            lastSuccessPainter
        }
        if (displayPainter != null) {
            Image(
                painter = displayPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * Immersive full-screen live view, shown in-place on the cameras page when a
 * tile is tapped. Hides the system bars while active, fills the display with the
 * selected camera's feed, supports pinch-to-zoom / pan / double-tap-to-zoom and
 * play-pause, and exits on back press or the close control. System bars are
 * restored on dispose.
 */
@Composable
private fun FullscreenCameraView(
    card: CameraCard,
    snapshotUrl: String?,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(onBack = onClose)

    var playing by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        LiveVideoFrame(snapshotUrl = snapshotUrl, playing = playing)

        if (!playing) {
            PausedOverlay()
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                card.camera.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { playing = !playing },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Renders the live feed with client-side pinch-to-zoom, pan and double-tap-zoom.
 * Follows the polled [snapshotUrl] while [playing]; when paused, holds the last
 * frame so the image freezes without tearing down the view.
 */
@Composable
private fun LiveVideoFrame(
    snapshotUrl: String?,
    playing: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Digital (client-side) zoom applied to the rendered frame. State lives here
    // so pinch/pan persists as new snapshots are swapped in.
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Advance the shown frame only while playing so pausing freezes the image.
    var displayedUrl by remember { mutableStateOf(snapshotUrl) }
    LaunchedEffect(snapshotUrl, playing) {
        if (playing && snapshotUrl != null) displayedUrl = snapshotUrl
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    offset = if (newScale > MIN_ZOOM) {
                        // Keep the magnified frame within the container bounds.
                        val maxX = (containerSize.width * (newScale - 1f)) / 2f
                        val maxY = (containerSize.height * (newScale - 1f)) / 2f
                        Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    } else {
                        Offset.Zero
                    }
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > MIN_ZOOM) {
                            scale = MIN_ZOOM
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (displayedUrl != null) {
            var lastSuccessPainter by remember { mutableStateOf<Painter?>(null) }

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(displayedUrl)
                    .crossfade(false)
                    .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
                    .build(),
                contentDescription = "Live view",
                onState = { coilState ->
                    if (coilState is AsyncImagePainter.State.Success) {
                        lastSuccessPainter = coilState.painter
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val painterState by painter.state.collectAsStateWithLifecycle()
                val displayPainter = if (painterState is AsyncImagePainter.State.Success) {
                    painterState.painter
                } else {
                    lastSuccessPainter
                }

                if (displayPainter != null) {
                    Image(
                        painter = displayPainter,
                        contentDescription = "Live view",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun PausedOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Resume",
            tint = Color.White,
            modifier = Modifier.size(64.dp),
        )
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.error,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "LIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }
    }
}
