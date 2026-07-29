package com.daygle.aicamera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Invoke [onPause] when the composition's lifecycle owner pauses and [onResume]
 * when it resumes. Used to stop live snapshot polling while the app is
 * backgrounded so it isn't wastefully hammering the server.
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
