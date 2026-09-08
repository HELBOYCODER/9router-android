# 9Router for Android

A **production-oriented Android application** that starts and manages a self-hosted
instance of [9Router](https://9router.com/) through the official Termux RUN_COMMAND
integration. Termux owns the Android-compatible Node.js runtime; the Android app
handles setup, lifecycle, health checks, notifications, and UI.

It bundles a Node.js runtime for Android ARM64 plus the compiled 9Router
standalone build, and exposes the local OpenAI-compatible API at:

```
http://127.0.0.1:20128/v1
```

with a WebView dashboard at:

```
http://127.0.0.1:20128/dashboard
```

This wrapper is deliberately kept independent from the upstream 9Router project:
it does not fork or rewrite the routing/provider logic. Secrets are generated
fresh per instance and never hard-coded, never committed, and never logged.

---

## What it does

| Feature | Status |
|--------|--------|
| **Install Prerequisites button** (instant setup of Node.js, npm, 9Router) | ✅ |
| **CONNECT / DISCONNECT button** (starts daemon & probes health) | ✅ |
| **Reinstall with Specs button** (backup/restore db, update package, preserve keys) | ✅ |
| Bundled offline assets & installer scripts | ✅ |
| Persistent Android Foreground Service | ✅ |
| Termux Node.js + 9Router bootstrap | ✅ (automatic prerequisite check/install) |
| OpenAI-compatible `/v1` endpoint | ✅ |
| Dashboard in WebView | ✅ |
| Instance manager (start / stop / restart / reinit) | ✅ |
| Health checking | ✅ |
| Automatic crash recovery with exponential backoff | ✅ |
| Secure secret generation + encrypted storage | ✅ (Android Keystore) |
| Configurable host / port / endpoint | ✅ |
| LAN mode (opt-in, warned) | ✅ |
| Log viewer (sanitized) with clear/copy/export | ✅ |
| Provider credentials never hard-coded | ✅ |
| Android 10+, ARM64, no root | ✅ |

---

## Repository layout

```
9router-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/…      (Gradle 8.9 wrapper)
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ninerouter/android/
│       │   ├── NineRouterApp.kt                 (Application, auto-resume)
│       │   ├── MainActivity.kt
│       │   ├── data/SettingsRepository.kt       (DataStore, non-secret settings)
│       │   ├── model/RouterState.kt             (status/state/log/model)
│       │   ├── model/Settings.kt
│       │   ├── router/
│       │   │   ├── RouterManager.kt             (orchestrator + auto-recovery)
│       │   │   ├── RouterProcess.kt             (spawn/manage node process)
│       │   │   ├── HealthChecker.kt             (OpenAI-style /v1/models probe)
│       │   │   ├── InstanceManager.kt           (data dirs, config, .env)
│       │   │   ├── RouterRuntime.kt             (extract bundle from assets)
│       │   │   └── LogStore.kt                  (sanitized in-memory log buffer)
│       │   ├── security/
│       │   │   ├── SecureStorage.kt             (Keystore-backed encrypted prefs)
│       │   │   └── SecretGenerator.kt           (SecureRandom)
│       │   ├── service/
│       │   │   └── RouterForegroundService.kt   (foreground service + actions)
│       │   ├── ui/
│       │   │   ├── MainScreen.kt
│       │   │   ├── SettingsScreen.kt
│       │   │   ├── LogsScreen.kt
│       │   │   ├── ProvidersScreen.kt
│       │   │   ├── DashboardActivity.kt         (WebView dashboard)
│       │   │   ├── components/Components.kt
│       │   │   └── theme/{Color,Type,Theme}.kt
│       │   ├── util/
│       │   │   ├── NetworkUtils.kt
│       │   │   └── AppLogger.kt                 (log sanitizer)
│       │   └── viewmodel/RouterViewModel.kt
│       └── res/                                 (icons, themes, strings, layouts)
├── scripts/
│   └── package_runtime.sh                       (host-side runtime packaging)
└── docs/
    ├── ARCHITECTURE.md
    ├── BUILD.md
    └── TROUBLESHOOTING.md
```

---

## Quick start

### 1. Install and prepare Termux on the phone

Install Termux from F-Droid or the official GitHub release, open it once, and set
this property in `~/.termux/termux.properties`:

```text
allow-external-apps=true
```

In Android Settings grant the 9Router app **Run commands in Termux** under
Additional permissions. On first CONNECT the app checks `pkg`, Node.js/npm, git,
and curl, installs missing packages, clones upstream 9Router source,
installs dependencies, builds the Next.js standalone server, generates local
secrets, registers `termux-services`, and starts the server in Termux.

#### Alternative: 1-line Termux installation (Hermes style)

If you prefer to bootstrap and run 9Router directly inside Termux:

```bash
pkg install -y curl
curl -fsSL https://raw.githubusercontent.com/3krbkbkrbrbg/9router-android/main/scripts/termux-install.sh | bash
```

This installs all dependencies, builds 9Router standalone, and sets up `termux-services` for persistent 24/7 background execution.

Android does not allow another app to silently install APKs or grant permissions;
those steps require user approval.

### 2. Build the APK (Android Studio / Android SDK)

Open the project in **Android Studio** (Ladybug+ / Hedgehog+ is fine) and let it
sync, then hit **Run**. Or from a CLI with the Android SDK:

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # signed release (configure signing in app/build.gradle.kts)
```

Built APKs:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

See [docs/BUILD.md](./docs/BUILD.md) for full build instructions, signing, and the
`armeabi-v7a`/`x86_64` notes.

---

## How it works under the hood

1. Tap **CONNECT**.
2. The app starts `RouterForegroundService`.
3. `InstanceManager` builds a fresh per-instance configuration and secrets.
4. `TermuxIntegration` sends a RUN_COMMAND intent to Termux. Termux checks/install
   prerequisites, builds upstream 9Router, and starts `node custom-server.js`.
5. `HealthChecker` probes `http://127.0.0.1:20128/v1/models` until the server
   answers. Any HTTP response = server alive; 2xx = fully ready.
7. The service posts an ongoing notification (Local API + status + actions).
8. If the process crashes while CONNECT is on, `RouterManager` restarts it with
   exponential backoff up to the configured max.

See [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) for the runtime decision and
known limits.

---

## Security

- **No hard-coded credentials** anywhere. Secrets come from `SecureStorage`
  (AES-256 encrypted with an Android Keystore-wrapped master key).
- Every re-initialized instance **rotates its secrets**.
- **Localhost-only by default** (`127.0.0.1`). LAN mode (`0.0.0.0`) is opt-in and
  shows an explicit warning.
- **Log sanitizer** (`AppLogger.sanitize`) redacts bearer tokens, `sk-…` keys,
  passwords, `JWT_SECRET=…`, etc. before anything is shown or exported.
- Provider credentials are entered in the dashboard by the user with accounts they
  own. This app never auto-generates/refreshes/rotates provider tokens, and never
  attempts to bypass provider quotas or authentication controls.
- Runtime data and log files are app-private.

---

## Requirements

- **Android 10 (API 29) or newer**
- Termux installed from F-Droid or the official GitHub release
- Termux: `allow-external-apps=true`
- Android permission: 9Router → Additional permissions → Run commands in Termux
- No root; Termux is the managed Android runtime

## Documentation

- [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) — runtime decision, upstream
  pin, patches, build/runtime requirements, limitations.
- [docs/BUILD.md](./docs/BUILD.md) — full build instructions.
- [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md) — common issues and fixes.

## License / upstream

- This wrapper is provided under the MIT license (see `LICENSE`).
- Upstream 9Router: https://github.com/decolua/9router (pin tracked in
  `runtime-manifest.json` and `docs/ARCHITECTURE.md`).
- 9Router software is free/open-source; it never charges you. You pay providers
  directly, if at all. This app does not modify that relationship.
