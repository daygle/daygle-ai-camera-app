# Implementation Plan - Fix MediaCodec Dead Thread Error on Android 17

The application is experiencing a `java.lang.IllegalStateException: Handler (android.media.MediaCodec$EventHandler) sending message to a Handler on a dead thread` crash. This is a known issue on Android 17 (API 37) due to the new **DeliQueue** (a lock-free `MessageQueue` implementation). The crash occurs when `MediaCodec` attempts to post a callback to a thread that has already been terminated or is in the process of shutting down.

In this app, the issue is triggered by `ExoPlayer` in `PlayerScreen.kt` when the player is being released or when the lifecycle changes.

## User Review Required

> [!IMPORTANT]
> The fix involves changing how `ExoPlayer` is managed in the `PlayerScreen` to ensure a cleaner shutdown. This includes explicitly stopping the player and clearing media items before release, which may slightly change the timing of when the player resources are freed.

> [!NOTE]
> This issue is specific to Android 17's new threading architecture. The proposed changes are backward compatible and follow best practices for `Media3/ExoPlayer` usage in Jetpack Compose.

## Proposed Changes

### UI Layer

#### [MODIFY] [PlayerScreen.kt](file:///C:/Users/glen/StudioProjects/daygle-ai-camera-app/app/src/main/java/com/daygle/aicamera/ui/player/PlayerScreen.kt)

- **Improve `rememberExoPlayer` lifecycle**:
    - Explicitly set the `Looper` to `Looper.getMainLooper()` to ensure all events are processed on a stable thread.
    - Move `player.prepare()` and `player.playWhenReady` out of the `remember` block and into a `LaunchedEffect`. This ensures they only run when the player is actually needed and avoids side effects during the composition phase.
    - Update the `DisposableEffect` to call `player.stop()` and `player.clearMediaItems()` before `player.release()`. This helps `MediaCodec` and other renderers shut down more gracefully before the playback thread is terminated.
- **Configure `PlayerView` for Android 17**:
    - Ensure `PlayerView` uses `SurfaceView` (default) but also handle the detachment more cleanly.

## Verification Plan

### Manual Verification
- Deploy the app to an Android 17 (API 37) emulator or device.
- Navigate to the **PlayerScreen** by opening a recording.
- Verify playback starts correctly.
- Navigate back to the recordings list rapidly multiple times to trigger the player creation/destruction lifecycle.
- Verify no crash occurs with the "dead thread" error.
- Background and foreground the app while playing to ensure `LifecycleResumeEffect` and `DisposableEffect` work together without issues.

### Automated Tests
- Since this is a hardware-dependent/threading race condition, unit tests are difficult. However, we will verify that the code compiles and the `ExoPlayer` lifecycle follows the expected pattern.
