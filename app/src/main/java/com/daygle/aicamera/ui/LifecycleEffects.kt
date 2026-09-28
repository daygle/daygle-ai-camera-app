package com.daygle.aicamera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Invoke [onPause] when the composition's lifecycle owner pauses and [onResume]
 * when it resumes. Used to stop live snapshot polling while the app is
 * backgrounded so it isn't wastefully hammering the server.
 *
 * [onPause] also runs when this effect leaves the composition (e.g. switching
 * to another home tab while the app stays resumed); otherwise the screen's
 * view model would keep polling for a screen nobody can see. Re-entering the
 * composition re-registers the observer, which replays ON_RESUME.
 */
@Composable
fun LifecycleResumeEffect(onPause: () -> Unit, onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPause by rememberUpdatedState(onPause)
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentOnResume()
                Lifecycle.Event.ON_PAUSE -> currentOnPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentOnPause()
        }
    }
}
