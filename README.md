# Daygle AI Camera - Android Application

A native Android client for a self-hosted [Daygle AI Camera](https://github.com/daygle/daygle-ai-camera)
server. Point it at your server, sign in, and view your cameras, live feeds,
detection events, and recordings from your phone.

The app is a **viewer** - all camera management, AI configuration, and rule
setup stays in the server's web dashboard. This client focuses on watching.

## Features

- **Connect to your server** - enter the server address and your Daygle AI
  Camera credentials. The connection is fault-tolerant, verified on sign-in,
  and remembered between launches.
- **Cameras dashboard** - a clean, vertical list of every configured camera
  with live snapshot thumbnails and online/offline status.
- **Live view** - tap a camera on the dashboard to expand its feed into an
  immersive, system-bar-free full-screen view right on the cameras page, with
  play/pause. Pinch to zoom (up to 5x, pan, double-tap to reset). Polling
  pauses automatically when the app is backgrounded.
- **Events** - a detailed detection/alert log showing specific event types
  (e.g., **Person**, **Dog Bark**) and confidence levels. Search, sort, and
  filter by detection type, date range, camera, trigger, label, or alerts
  only. Tap an event to open its annotated snapshot (detection boxes) - with
  pinch-to-zoom (up to 5x, pan, double-tap to reset) - or jump straight to the
  recording it triggered.
- **Recordings** - browse saved clips - each shown as a single entry even
  when it spans many detection events - and play them back in-app with a full
  video scrubber, pinch-to-zoom (up to 5x), and an immersive full-screen mode.
  Search and filter by camera, date range, trigger, or label.
- **Push alerts** - real notifications when your cameras detect an object or
  sound, delivered while the app is backgrounded (see below).
- **Settings** - theme (system/dark/light), 24-hour time format, live-view
  refresh rate, a permissions checklist, and sign-out (disconnects the server
  and forgets it on this device).

## How it connects

The server uses browser-style **session-cookie authentication with a CSRF
token** (there is no bearer/API-token endpoint), so the app reproduces the web
login handshake:

1. `GET /login` - the server sets a CSRF cookie and returns a form containing a
   matching `csrf_token`.
2. `POST /login` with form-encoded `username`, `password`, and that
   `csrf_token` - on success the server sets the session cookie.

The session cookie is then sent with every read request. All API calls the app
makes are read-only `GET`s, which need only the cookie (no CSRF header). A
shared OkHttp client with a cookie jar backs Retrofit (JSON), Coil (snapshots),
and Media3/ExoPlayer (recording playback), so all three ride the same session
and transparently re-authenticate if the session expires (a `401` triggers a
silent re-login and one retry).

### Server endpoints used

| Endpoint | Purpose |
| --- | --- |
| `POST /login` | Establish a session |
| `GET /api/cameras` | List configured cameras |
| `GET /api/cameras/health` | Per-camera online/offline state |
| `GET /api/live/snapshot?camera_id=` | Latest JPEG frame (live view) |
| `GET /api/events` | Detection / alert log |
| `GET /api/recordings` | Saved recordings |
| `GET /api/recordings/{id}/stream` | Range-request MP4 playback |
| `GET /api/settings/alert-push` | Discover the server's ntfy push config |

## Push alerts

The Daygle server sends detection alerts via **ntfy** - it POSTs to
`{server_url}/{topic}`. This app receives those alerts by **subscribing to the
same ntfy topic's live stream** (`GET {server_url}/{topic}/json`), the same
"instant delivery" model the official ntfy app uses for self-hosted servers.
Tapping an alert notification opens the triggering event's annotated snapshot
directly (the server includes the event ID in the alert body).

To turn it on: tap the **bell** icon on the home screen → **Auto-fill from
server** (reads the ntfy server/topic from your server's push settings) →
enable the switch and grant the notification permission.

The same screen (and Settings) includes a **permissions checklist** that walks
you through what alerts need - the notification permission and a
battery-optimization exemption so the listener isn't killed - plus a
**send test** button that posts a sample alert so you can confirm delivery
end to end.

A lightweight **foreground service** holds the streaming connection open and
raises a notification for each alert, with automatic reconnect and restart on
boot.

**What this requires / its limits:**

- Push must be **configured and enabled on the server** (its Settings →
  notifications → ntfy). The app only listens; the server does the sending.
- Delivery works while the app is backgrounded via an ongoing "watching for
  alerts" status notification. A periodic **WorkManager keep-alive** ensures
  the listener restarts automatically if Android ever kills it - no need to
  reopen the app. Uses **no cloud push infrastructure** (not Firebase/FCM).
- If your ntfy topic is access-protected and you sign in as a **viewer**, the
  server redacts the ntfy password; enter the ntfy username/password manually
  on the notifications screen. Public/unprotected topics need no credentials.
- On Android 13+ the app requests the notification permission the first time
  you enable alerts.

### Remote access via Cloudflare Tunnel

Instead of exposing the server directly with port forwarding, you can put the
server behind a **Cloudflare Tunnel**: the server

dials *out* to Cloudflare, so no ports are opened and the server never gets a
public IP. The app simply points at the resulting `https://` hostname.

If you protect the tunnel with **Cloudflare Access**, add a **service token**
(Zero Trust → Access → Service Auth) and enter its **Client ID / Client Secret**
in the app's connect screen under *Cloudflare Access (Optional)*. The app then
sends the `CF-Access-Client-Id` / `CF-Access-Client-Secret` headers on every
request - including the login handshake and the push-alert stream - so
Access lets the app through instead of redirecting it to a browser login page.
When the fields are empty, the app behaves exactly as before (no headers sent),
so servers not behind Access are unaffected.

### Custom headers

If your server requires an extra HTTP header on every request (for example an
API key or a reverse-proxy token), you can configure one in the connect screen
under *Custom Header (Optional)* - enter the header name and value. The header
is attached to **every** request, including the login handshake and the
push-alert stream, and its value is stored encrypted like your password and
redacted from the app's HTTP logs. Leave the fields empty to send no custom
header. (For Cloudflare Access, use the dedicated service-token fields above
instead.)

## Requirements

- Android 8.0 (API 26) or newer
- A reachable Daygle AI Camera server (LAN or a public HTTPS host)

> **Release builds require HTTPS**. The release network security config blocks
> cleartext HTTP so credentials and camera media are not sent in plaintext. A
> server address typed without a scheme is therefore assumed to be `https://`
> (e.g. `cam.example.com` → `https://cam.example.com`). Debug builds permit
> explicitly configured LAN HTTP/local certificates for development; type the
> full `http://` prefix for those (`http://192.168.1.20:8080`). Do not use that
> mode for an internet-facing deployment. See
> `app/src/main/res/xml/network_security_config.xml` and
> `network_security_config_debug.xml`.

## Building

This is a standard Gradle/Android Studio project (Kotlin + Jetpack Compose).

**Prerequisites:** JDK 17+, Android SDK with API 37 platform installed.

```bash
# Android Studio: open the project root and Run 'app'.
# Command line:
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease      # release APK (signing not configured by default)
```

CI runs on every push to `main` via GitHub Actions (`.github/workflows/android-build.yml`).

### Tech stack

| Layer | Libraries |
| --- | --- |
| Language | Kotlin 2.4.10 |
| UI | Jetpack Compose (BOM 2026.06.01), Material 3 + material-icons-extended |
| Build | AGP 9.3.1, **Gradle 9.6.1**, JDK 17 |
| Performance | Configuration Cache, Parallel Sync |
| Navigation | Navigation Compose 2.9.8 |
| DI | Hilt 2.60 (KSP), hilt-navigation-compose |
| Networking | OkHttp 5.4, Retrofit 3.0, kotlinx.serialization 1.11 |
| Images | Coil 3.5 (shares the authenticated OkHttp client) |
| Video | Media3 / ExoPlayer 1.10.1 |
| Storage | DataStore 1.2.1 |
| Background | WorkManager 2.11 (push keep-alive) |
| Architecture | MVVM, Hilt DI |

## Project layout

```
app/src/main/java/com/daygle/aicamera/
├── DaygleApp.kt                 # @HiltAndroidApp + Coil wiring (auth'ed OkHttp)
├── MainActivity.kt
├── data/
│   ├── AppPreferencesStore.kt   # Persisted app-level preferences (DataStore)
│   ├── CameraRepository.kt      # Domain layer
│   ├── DaygleApi.kt             # Retrofit endpoints
│   ├── NetworkExtensions.kt     # OkHttp/Media3 helpers
│   ├── NotificationSettingsStore.kt  # Persisted ntfy push config (DataStore)
│   ├── SessionManager.kt        # Cookie/CSRF login, OkHttp/Retrofit stack
│   ├── SettingsStore.kt         # Persisted server URL + credentials (DataStore)
│   └── model/Models.kt          # Serializable API models
├── di/
│   └── DataModule.kt            # Hilt DI bindings
├── push/
│   ├── BootReceiver.kt          # Resume the listener after reboot
│   ├── NtfyService.kt           # Foreground ntfy stream -> notifications
│   ├── PushController.kt        # Start/stop the listener from the saved config
│   └── PushKeepAliveWorker.kt   # WorkManager keep-alive for the listener
└── ui/
    ├── DaygleNavHost.kt         # Navigation graph + auth gating
    ├── Format.kt                # Timestamp/duration formatters
    ├── HomeScreen.kt            # Bottom-nav shell (cameras, events, recordings)
    ├── LifecycleEffects.kt      # Lifecycle-aware pause/resume helper
    ├── RootViewModel.kt         # Session restore -> start destination
    ├── components/Common.kt     # LoadingState, ErrorState, EmptyState
    ├── connect/
    │   ├── ConnectScreen.kt     # Server URL + credentials (+ Cloudflare Access)
    │   └── ConnectViewModel.kt
    ├── dashboard/
    │   ├── DashboardScreen.kt   # Camera list + in-place full-screen live view
    │   └── DashboardViewModel.kt
    ├── events/
    │   ├── EventsScreen.kt      # Detection log + snapshot/recording links
    │   └── EventsViewModel.kt
    ├── notifications/
    │   ├── NotificationsScreen.kt  # Push-alert settings + permission checklist
    │   └── NotificationsViewModel.kt
    ├── onboarding/
    │   └── OnboardingScreen.kt  # First-run intro before connect
    ├── permissions/
    │   └── AppPermissions.kt    # Permission checks + PermissionsChecklist
    ├── player/
    │   ├── PlayerScreen.kt      # ExoPlayer playback: scrubber, zoom, full screen
    │   └── PlayerViewModel.kt
    ├── recordings/
    │   ├── RecordingsScreen.kt  # Clip list (one entry spans many events)
    │   └── RecordingsViewModel.kt
    ├── settings/
    │   ├── SettingsScreen.kt    # Theme, time format, refresh rate, sign out
    │   └── SettingsViewModel.kt
    └── theme/Theme.kt           # Material 3 color scheme + status bar
```
