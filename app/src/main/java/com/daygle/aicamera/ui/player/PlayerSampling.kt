package com.daygle.aicamera.ui.player

import androidx.media3.common.Player
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Keeps UI state that follows playback (playhead, detection boxes) in step
 * with the player without burning battery while nothing moves.
 *
 * While the player is playing, [sample] runs after every [tick] (a frame, or
 * a short delay). While it is paused, buffering, ended or the app is in the
 * background, the loop suspends instead of polling, and [sample] runs only
 * when the player reports an event (a seek, a new duration, a size change),
 * so a paused clip costs no wake-ups. Must be called on the player's
 * (main) thread; runs until cancelled.
 */
internal suspend fun Player.sampleWhilePlaying(tick: suspend () -> Unit, sample: () -> Unit) {
    val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sample()
    }
    addListener(listener)
    try {
        while (true) {
            sample()
            if (isPlaying) tick() else awaitPlaying()
        }
    } finally {
        removeListener(listener)
    }
}

/** Suspends until the player starts playing (returns at once if it already is). */
private suspend fun Player.awaitPlaying() {
    if (isPlaying) return
    suspendCancellableCoroutine { continuation ->
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && continuation.isActive) {
                    removeListener(this)
                    continuation.resume(Unit)
                }
            }
        }
        addListener(listener)
        continuation.invokeOnCancellation { removeListener(listener) }
    }
}
