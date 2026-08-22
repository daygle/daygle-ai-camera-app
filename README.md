# Daygle AI Camera - Android Application

[![Android CI](https://github.com/daygle/daygle-ai-camera-app/actions/workflows/android-build.yml/badge.svg)](https://github.com/daygle/daygle-ai-camera-app/actions/workflows/android-build.yml)
[![CodeQL](https://github.com/daygle/daygle-ai-camera-app/actions/workflows/codeql.yml/badge.svg)](https://github.com/daygle/daygle-ai-camera-app/actions/workflows/codeql.yml)

A modern, native Android client for your self-hosted [Daygle AI Camera](https://github.com/daygle/daygle-ai-camera) server. View live feeds, browse detection events, and play back recordings with high-performance native tools.

The app is a **dedicated viewer** — while camera management and AI rules stay in your server's web dashboard, this client provides a fluid, mobile-first experience for monitoring your home or business.

---

## 📖 Table of Contents
- [Features](#features)
- [How it Connects](#how-it-connects)
- [Push Alerts (ntfy)](#push-alerts)
- [Advanced Connectivity](#advanced-connectivity)
- [Requirements & Building](#requirements--building)
- [Tech Stack](#tech-stack)
- [Project Layout](#project-layout)

---

## ✨ Features

- **Seamless Connection** — Connect via local IP or public HTTPS. Verified on sign-in and remembered between sessions.
- **Cameras Dashboard** — Clean grid view of all configured cameras with live snapshot thumbnails and real-time status.
- **Immersive Live View** — Tap any camera for a full-screen, landscape feed with pinch-to-zoom (up to 5x) and fluid panning.
- **Deep Event Log** — Filter alerts by type (Person, Dog Bark, etc.), date, or camera. Jump from an event directly to its annotated snapshot or triggered recording.
- **Video Recordings** — High-performance playback of saved clips with a full video scrubber and pinch-to-zoom support.
- **Real-time Push Alerts** — Stay notified with instant object/sound detection alerts delivered while the app is backgrounded.
- **Adaptive UI** — Full support for light/dark themes and 12/24-hour time formats.

---

## 🔒 How it Connects

The server uses browser-style **session-cookie authentication with a CSRF token**. The app reproduces this secure handshake:

1. **Token Fetch**: Performs a `GET /login` to acquire a CSRF token.
2. **Authentication**: Performs a `POST /login` with credentials and the token to establish a session.
3. **Session Persistence**: The session cookie is managed by a shared OkHttp client, used transparently by Retrofit, Coil (images), and ExoPlayer (video).

> [!TIP]
> Authentication is automatic. If your session expires, the app silently re-authenticates and retries your request without interrupting your workflow.

---

## 🔔 Push Alerts

Daygle uses [ntfy](https://ntfy.sh) for secure, real-time alerts without relying on cloud push infrastructure (FCM/Firebase).

- **Instant Delivery**: The app subscribes to your server's ntfy topic via a lightweight foreground service.
- **Auto-Config**: Tap the **bell icon** → **Auto-fill from server** to instantly sync your ntfy settings from your Daygle dashboard.
- **Persistence**: A WorkManager-backed keep-alive ensures you never miss an alert, even if Android restarts the background service.

---

## 🌐 Advanced Connectivity

### Cloudflare Access
If your server is behind a Cloudflare Tunnel protected by **Cloudflare Access**, you can enter your **Service Token** (Client ID and Secret) in the *Network & Proxy Settings*. This allows the app to bypass the Access login page automatically.

### Custom Headers
For environments requiring specific HTTP headers (like proxy API keys), configure them under *Advanced Options*. These headers are attached to **every** request, including the login handshake and media streams.

---

## 🛠 Requirements & Building

### Prerequisites
- **Target OS**: Android 8.0 (API 26) or newer.
- **Daygle Server**: A reachable Daygle AI Camera server (LAN or public HTTPS).
- **Development**: JDK 21 and Android SDK with API 37 platform.

> [!IMPORTANT]
> **Release builds require HTTPS**. For security, the app forbids cleartext HTTP in production. Use a valid SSL certificate or a Cloudflare Tunnel for remote access. Debug builds permit local `http://` addresses for development.

### Build Commands
```bash
./gradlew assembleDebug    # Debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease  # Release APK
```

---

## 🏗 Tech Stack

| Layer | Technology |
| --- | --- |
| **Language** | Kotlin 2.4.10 (JDK 21) |
| **UI Framework** | Jetpack Compose (Material 3, Adaptive Layouts) |
| **Build System** | Gradle 9.7.1 with AGP 9.3.1 |
| **Networking** | OkHttp 5.4, Retrofit 3.0, Kotlinx Serialization |
| **Dependency Injection** | Hilt 2.60 (KSP) |
| **Image / Video** | Coil 3.5, Media3 / ExoPlayer 1.11.1 |
| **Architecture** | MVVM / MVI with DataStore persistence |

---

## 📁 Project Layout

```text
app/src/main/java/com/daygle/aicamera/
├── DaygleApp.kt                 # App-root & Coil configuration
├── data/
│   ├── CameraRepository.kt      # Unified domain access
│   ├── SessionManager.kt        # Auth & API state orchestration
│   ├── Interceptors.kt          # Auth, Cloudflare & Custom Header logic
│   ├── NetworkExtensions.kt     # Safe Coroutine execution & error mapping
│   └── SettingsStore.kt         # Secure credential & URL persistence
├── push/
│   ├── NtfyService.kt           # Foreground streaming alert listener
│   └── PushController.kt        # Alert lifecycle management
└── ui/
    ├── DaygleNavHost.kt         # Route definition & auth gating
    ├── ErrorUtils.kt            # Centralized UI error reporting
    ├── dashboard/               # Live camera grid & full-screen live view
    ├── events/                  # Filterable detection log & alert cards
    ├── player/                  # Native video playback with pinch-to-zoom
    ├── recordings/              # Historical clip management
    └── settings/                # App preferences & server details
```
