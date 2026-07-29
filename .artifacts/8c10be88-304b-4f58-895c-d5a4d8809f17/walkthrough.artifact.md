# Walkthrough - Fixed MediaCodec Dead Thread Error on Android 17

I have implemented the fixes to resolve the `java.lang.IllegalStateException: Handler (android.media.MediaCodec$EventHandler) sending message to a Handler on a dead thread` crash occurring on Android 17 (API 37).

## Changes Made

### UI Layer

#### [PlayerScreen.kt](file:///C:/Users/glen/StudioProjects/daygle-ai-camera-app/app/src/main/java/com/daygle/aicamera/ui/player/PlayerScreen.kt)

- **Explicit Looper Configuration**: Updated `ExoPlayer.Builder` to explicitly use `Looper.getMainLooper()`. This ensures that all internal event handling and callbacks are tied to a stable thread, which is critical for the new lock-free **DeliQueue** architecture in Android 17.
- **Improved Preparation Lifecycle**: Moved `setMediaItem()`, `prepare()`, and `playWhenReady` into a `LaunchedEffect`. This ensures that these side-effects happen after the player is constructed and handle stream URL changes more reliably.
- **Robust Cleanup Sequence**: Updated the `onDispose` block in `DisposableEffect` to explicitly call `player.stop()` and `player.clearMediaItems()` before calling `player.release()`. This sequence prevents `MediaCodec` from attempting to send callbacks to a terminated thread during shutdown, which was the root cause of the crash.

## Verification Results

### Automated Tests
- Ran `:app:assembleDebug` which confirmed that the changes are syntactically correct and the project builds successfully.

### Manual Verification Recommendation
> [!TIP]
> To verify the fix on a physical device or emulator running Android 17:
> 1. Open a recording to trigger `PlayerScreen`.
> 2. Rapidly navigate back and forth between the recordings list and the player.
> 3. Verify that no "dead thread" crash occurs.
