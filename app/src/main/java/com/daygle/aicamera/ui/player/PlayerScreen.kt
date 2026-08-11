package com.daygle.aicamera.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.daygle.aicamera.R
import com.daygle.aicamera.ui.components.ErrorState

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    recordingId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val player = viewModel.player

    var fullscreen by rememberSaveable { mutableStateOf(false) }

    if (fullscreen && error == null) {
        FullscreenPlayer(
            player = player,
            onExitFullscreen = { fullscreen = false },
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Recording #$recordingId",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (error == null) {
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fullscreen),
                                contentDescription = "Full screen",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (error != null) {
            ErrorState(
                message = error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding)
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    ZoomablePlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Immersive full-screen playback. Hides the system bars while active and fills
 * the display; the Media3 transport controls and pinch-to-zoom remain available.
 * Exits on back press or the on-screen control; system bars are restored on dispose.
 */
@Composable
private fun FullscreenPlayer(
    player: Player,
    onExitFullscreen: () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val activity = view.context as? Activity
            val window = activity?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val originalOrientation = activity?.requestedOrientation
            // Rotate the screen to landscape for a wide, immersive playback view.
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                // Restore the previous orientation preference on exit.
                activity?.requestedOrientation =
                    originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
        ZoomablePlayerSurface(player = player, modifier = Modifier.fillMaxSize())

        FilledTonalIconButton(
            onClick = onExitFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp)
                .size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fullscreen_exit),
                contentDescription = "Exit full screen",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * ExoPlayer video surface with digital pinch-to-zoom. The [PlayerView] is inflated
 * with a TextureView surface (see res/layout/view_zoomable_player.xml) so the video
 * follows the [graphicsLayer] transform. Only transform gestures are handled here —
 * no tap detector — so single taps still reach the Media3 controller (play/pause,
 * scrub, show/hide). Zoom is clamped and panning is kept within the view bounds;
 * pinching back to 1x recenters.
 */
@Composable
private fun ZoomablePlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    offset = if (newScale > MIN_ZOOM) {
                        // Keep the magnified video within the container bounds.
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
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx)
                    .inflate(R.layout.view_zoomable_player, FrameLayout(ctx), false) as PlayerView).apply {
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
