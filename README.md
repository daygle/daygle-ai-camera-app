# Daygle AI Camera — Android app

A native Android client for a self-hosted [Daygle AI Camera](https://github.com/daygle/daygle-ai-camera)
server. Point it at your server, sign in, and view your cameras, live feeds,
detection events, and recordings from your phone.

The app is a **viewer** — all camera management, AI configuration, and rule
setup stays in the server's web dashboard. This client focuses on watching.

## Features

- **Connect to your server** — enter the server address and your Daygle AI
  Camera credentials. The connection is verified on sign-in and remembered
  between launches.
- **Cameras dashboard** — a grid of every configured camera with live snapshot
  thumbnails, online/offline status, and a system summary (cameras online, AI
  backend, uptime).
- **Live view** — tap a camera for a near-live feed (refreshed snapshots) with
  play/pause and the current stream resolution. Polling pauses automatically
  when the app is backgrounded.
- **Events** — the detection/alert log, filterable to alerts only.
- **Recordings** — browse saved clips and play them back in-app with a full
  video scrubber.
- **Push alerts** — real notifications when your cameras detect an object or
  sound, delivered while the app is backgrounded (see below).

## How it connects

The server uses browser-style **session-cookie authentication with a CSRF
token** (there is no bearer/API-token endpoint), so the app reproduces the web
login handshake:

1. `GET /login` — the server sets a CSRF cookie and returns a form containing a
   matching `csrf_token`.
2. `POST /login` with form-encoded `username`, `password`, and that
   `csrf_token` — on success the server sets the session cookie.

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
| `GET /api/status` | System / AI / camera status |
| `GET /api/live/snapshot?camera_id=` | Latest JPEG frame (live view) |
| `GET /api/events` | Detection / alert log |
| `GET /api/recordings` | Saved recordings |
| `GET /api/recordings/{id}/stream` | Range-request MP4 playback |
| `GET /api/settings/alert-push` | Discover the server's ntfy push config |

## Push alerts

The Daygle server sends detection alerts via **ntfy** — it POSTs to
`{server_url}/{topic}`. This app receives those alerts by **subscribing to the
same ntfy topic's live stream** (`GET {server_url}/{topic}/json`), the same
"instant delivery" model the official ntfy app uses for self-hosted servers.

To turn it on: tap the **bell** icon on the home screen → **Auto-fill from
server** (reads the ntfy server/topic from your server's push settings) →
enable the switch and grant the notification permission.

A lightweight **foreground service** holds the streaming connection open and
raises a notification for each alert, with automatic reconnect and restart on
boot.

**What this requires / its limits:**

- Push must be **configured and enabled on the server** (its Settings →
  notifications → ntfy). The app only listens; the server does the sending.
- Delivery works while the app is backgrounded via an ongoing "watching for
  alerts" status notification. It is **not** Firebase/FCM — there's no cloud
  push project — so if Android kills the service under heavy memory pressure
  it resumes the next time you open the app (or on reboot).
- If your ntfy topic is access-protected and you sign in as a **viewer**, the
  server redacts the ntfy password; enter the ntfy username/password manually
  on the notifications screen. Public/unprotected topics need no credentials.
- On Android 13+ the app requests the notification permission the first time
  you enable alerts.

## Requirements

- Android 8.0 (API 26) or newer
- A reachable Daygle AI Camera server (LAN, VPN, or a public HTTPS host)

> **Cleartext HTTP** is allowed because these servers are commonly hosted on a
> LAN address over plain HTTP (e.g. `http://192.168.1.20:8080`). Prefer HTTPS
> whenever your server is reachable over TLS. See
> `app/src/main/res/xml/network_security_config.xml`.

## Building

This is a standard Gradle/Android Studio project (Kotlin + Jetpack Compose).

```bash
# Android Studio: open the project root and Run 'app'.
# Command line (with the Android SDK installed and ANDROID_HOME set):
./gradlew assembleDebug
# The APK is written to app/build/outputs/apk/debug/.
```

### Tech stack

- Kotlin, Jetpack Compose, Material 3
- Navigation Compose
- OkHttp + Retrofit + kotlinx.serialization
- Coil (image loading for snapshots)
- Media3 / ExoPlayer (recording playback)
- DataStore (connection settings)

## Project layout

```
app/src/main/java/com/daygle/aicamera/
├── DaygleApp.kt              # Application + service locator + Coil wiring
├── MainActivity.kt
├── data/
│   ├── SettingsStore.kt      # Persisted server URL + credentials (DataStore)
│   ├── SessionManager.kt     # Cookie/CSRF login, OkHttp/Retrofit stack
│   ├── DaygleApi.kt          # Retrofit endpoints
│   ├── CameraRepository.kt   # Domain layer
│   └── model/Models.kt       # Serializable API models
├── push/
│   ├── NtfyService.kt        # Foreground ntfy stream → notifications
│   ├── PushController.kt     # Start/stop the listener from the saved config
│   └── BootReceiver.kt       # Resume the listener after reboot
└── ui/
    ├── DaygleNavHost.kt      # Navigation graph
    ├── HomeScreen.kt         # Bottom-nav shell
    ├── connect/              # Sign-in
    ├── dashboard/            # Cameras grid
    ├── live/                 # Live snapshot view
    ├── events/               # Detection log
    ├── recordings/           # Clip list
    ├── player/               # ExoPlayer playback
    └── notifications/        # Push-alert settings
```
