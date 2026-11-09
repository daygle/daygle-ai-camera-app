package com.daygle.aicamera.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/*
 * Window-level effects shared by the full-screen views (video player, live
 * view, snapshot viewer). Each applies while its composable is in the
 * composition and is undone when it leaves.
 */

/**
 * Forces the hosting [Activity] into landscape, restoring the previous
 * orientation preference when it leaves, so every full-screen view rotates
 * consistently.
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

/** Hides the status and navigation bars (a swipe shows them briefly) for an immersive view. */
@Composable
fun HideSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Keeps the display awake, e.g. while a live feed is on screen. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * Walks the [ContextWrapper] chain to find the hosting [Activity]. A Compose
 * `Dialog` runs in its own window whose context may be wrapped, so a direct cast
 * is not reliable.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
