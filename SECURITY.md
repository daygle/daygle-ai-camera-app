# Security Policy

The Daygle AI Camera **Android app** is a viewer client for the self-hosted
[Daygle AI Camera server](https://github.com/daygle/daygle-ai-camera). All
camera management, credentials, AI configuration, and rule setup live on the
server; this app only reads data over the network.

## Supported versions

The app tracks the server's release cadence. Only the latest stable release of
the app and the server are supported with security fixes.

| Component           | Supported          |
| ------------------- | ------------------ |
| Latest Android app release | ✅ |
| Latest server release       | ✅ |
| Older releases              | ❌ |

## Security model

- **Transport**: all traffic between the app and the server must use HTTPS
  (or an equally secure tunnel). The app refuses to treat plain HTTP as safe
  for credentials; configure the server behind TLS (e.g. a reverse proxy or
  Cloudflare Tunnel) before entering credentials in the app.
- **Credentials**: the app stores only the server address and session state.
  It never stores camera credentials - those live on the server. The session
  cookie is scoped to the server origin and is used for all requests; a `401`
  triggers a silent re-login.
- **Push alerts**: notifications are delivered over the ntfy-compatible push
  endpoint configured on the server. The app does not include push secrets or
  API keys.
- **Data at rest**: the app keeps its local settings (theme, time format,
  server address, session) in Android's encrypted-preferences-backed
  DataStore. We recommend enabling full-disk encryption on the device.

## Reporting a vulnerability

If you believe you have found a security vulnerability in the Android app or
the Daygle AI Camera server, please **do not open a public issue**.

Report it privately using one of these channels:

- GitHub **Security Advisories** (preferred):
  - App: <https://github.com/daygle/daygle-ai-camera-app/security/advisories/new>
  - Server: <https://github.com/daygle/daygle-ai-camera/security/advisories/new>
- Or email the maintainer with the subject `[SECURITY]` - see the repository
  profile / commits for the current maintainer contact.

Please include:

1. The component and version affected (app and/or server).
2. A description of the vulnerability and its impact.
3. Steps to reproduce, or a minimal proof of concept.
4. Any suggested fix, if you have one.

You will receive an acknowledgement within 72 hours, and a detailed response
(including next steps and a fix timeline) as soon as we have assessed the
report. Please allow us time to release a fix before disclosing the issue
publicly.

## Responsible disclosure

We ask that you:

- Keep details of the vulnerability private until a fix is released.
- Avoid accessing, modifying, or deleting data beyond what is needed to
  demonstrate the issue.
- Give the project reasonable time to respond and release a fix before any
  public disclosure.

We will credit researchers who responsibly report issues, if they wish to be
named.

## General hardening notes

- Keep the app and server updated to the latest releases.
- Run the server on Linux with a dedicated, non-root service user.
- Expose the server only through TLS (reverse proxy or tunnel); never expose
  the raw HTTP port to the public internet.
- Use strong, unique passwords for the server admin account and every camera.
- Review the server's audit log periodically for unexpected admin actions.
