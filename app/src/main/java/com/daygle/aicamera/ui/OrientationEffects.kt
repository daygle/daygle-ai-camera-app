package com.daygle.aicamera.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Forces the hosting [Activity] into landscape for as long as this composable
 * stays in the composition, restoring the previous orientation preference when
 * it leaves. Shared by the full-screen video player, the live view, and the
 * snapshot viewer so they all rotate consistently.
 */
@Composable
fun ForceLandscape() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/**
 * Walks the [ContextWrapper] chain to find the hosting [Activity]. A Compose
 * `Dialog` runs in its own window whose context may be wrapped, so a direct cast
 * is not reliable.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
