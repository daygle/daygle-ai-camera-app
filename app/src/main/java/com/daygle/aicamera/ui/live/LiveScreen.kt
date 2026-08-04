package com.daygle.aicamera.ui.live

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.daygle.aicamera.R
import com.daygle.aicamera.ui.LifecycleResumeEffect

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Stop polling while backgrounded; resume on return.
    LifecycleResumeEffect(onPause = viewModel::pause, onResume = viewModel::resume)

    var fullscreen by rememberSaveable { mutableStateOf(false) }

    if (fullscreen) {
        FullscreenLive(
            state = state,
            viewModel = viewModel,
            onExitFullscreen = { fullscreen = false },
        )
        return
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    // Use the non-deprecated breakpoint check
    val isExpanded = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        state.cameraName.ifBlank { "Live" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    LiveVideoFrame(state, viewModel)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ControlBar(
                        playing = state.playing,
                        resolution = state.status?.resolution?.let { r ->
                            if (r.width > 0 && r.height > 0) "${r.width}×${r.height}" else null
                        },
                        onTogglePlay = viewModel::togglePlayback,
                        onEnterFullscreen = { fullscreen = true },
                    )

                    // placeholder for additional camera details or event log in expanded view
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Text("Camera Details", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.size(8.dp))
                            Text("ID: ${state.cameraId}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .aspectRatio(16f / 9f),
                    color = Color.Black
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LiveVideoFrame(state, viewModel)
                    }
                }

                ControlBar(
                    playing = state.playing,
                    resolution = state.status?.resolution?.let { r ->
                        if (r.width > 0 && r.height > 0) "${r.width}×${r.height}" else null
                    },
                    onTogglePlay = viewModel::togglePlayback,
                    onEnterFullscreen = { fullscreen = true },
                )
            }
        }
    }
}

/**
 * Immersive full-screen playback. Hides the system bars while active, fills the
 * whole display with the live frame (pinch-to-zoom still available), and exits
 * on back press or the on-screen control. System bars are restored on dispose.
 */
@Composable
private fun FullscreenLive(
    state: LiveUiState,
    viewModel: LiveViewModel,
    onExitFullscreen: () -> Unit,
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

    BackHandler(onBack = onExitFullscreen)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        LiveVideoFrame(state, viewModel)

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = viewModel::togglePlayback,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                    modifier = Modifier.size(24.dp),
                )
            }
            FilledTonalIconButton(
                onClick = onExitFullscreen,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fullscreen_exit),
                    contentDescription = "Exit full screen",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveVideoFrame(
    state: LiveUiState,
    viewModel: LiveViewModel,
) {
    val context = LocalContext.current

    // Digital (client-side) zoom applied to the rendered frame. State lives here
    // so pinch/pan persists as new snapshots are swapped in.
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

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
        if (state.frameUrl != null) {
            var lastSuccessPainter by remember {
                mutableStateOf<Painter?>(null)
            }

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(state.frameUrl)
                    .crossfade(false)
                    .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
                    .build(),
                contentDescription = "Live view",
                onState = { coilState ->
                    if (coilState is AsyncImagePainter.State.Success) {
                        lastSuccessPainter = coilState.painter
                        viewModel.fetchNextFrame()
                    } else if (coilState is AsyncImagePainter.State.Error) {
                        viewModel.fetchNextFrame()
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
        }
        if (state.frameUrl == null) {
            CircularProgressIndicator(color = Color.White)
        }
        if (!state.playing) {
            PausedOverlay()
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
private fun ControlBar(
    playing: Boolean,
    resolution: String?,
    onTogglePlay: () -> Unit,
    onEnterFullscreen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (playing) "Live Feed" else "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (resolution != null) {
                    Text(
                        resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            FilledTonalIconButton(
                onClick = onEnterFullscreen,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fullscreen),
                    contentDescription = "Full screen",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
