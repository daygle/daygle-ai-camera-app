package com.daygle.aicamera.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.daygle.aicamera.ui.LifecycleResumeEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = viewModel(factory = LiveViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Stop polling while backgrounded; resume on return.
    LifecycleResumeEffect(onPause = viewModel::pause, onResume = viewModel::resume)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.cameraName.ifBlank { "Live" }) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                if (state.frameUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(state.frameUrl)
                            .crossfade(false)
                            // Live frames are unique per poll; caching them would
                            // churn the disk cache with single-use images.
                            .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                            .build(),
                        contentDescription = "Live view",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.frameUrl == null) {
                    CircularProgressIndicator(color = Color.White)
                }
                if (!state.playing) {
                    PausedOverlay()
                }
            }

            ControlBar(
                playing = state.playing,
                resolution = state.status?.resolution?.let { r ->
                    if (r.width > 0 && r.height > 0) "${r.width}×${r.height}" else null
                },
                onTogglePlay = viewModel::togglePlayback,
            )
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
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun ControlBar(
    playing: Boolean,
    resolution: String?,
    onTogglePlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                if (playing) "Live" else "Paused",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (resolution != null) {
                Text(
                    resolution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
