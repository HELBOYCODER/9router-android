# 9Router for Android

A **production-oriented Android application** that packages and runs a self-hosted
instance of [9Router](https://9router.com/) directly on the device — with no root,
no Termux, and no manual shell usage.

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
| CONNECT / DISCONNECT button | ✅ |
| Persistent Android Foreground Service | ✅ |
| Embedded Node.js + 9Router bundle | ✅ (packaged via script) |
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

### 1. Package the embedded runtime (one-time, on a host with Node.js)

9Router needs its compiled Next.js **standalone** bundle plus a Node.js binary
compiled for **Android ARM64**. The app does not include the upstream project as
source; it ships the assembled runtime.

```bash
# From the repo root on a Linux/macOS host with Node 18+:
./scripts/package_runtime.sh --node /path/to/node-aarch64-android
```

> **About the Node.js binary:** JavaScript is architecture-independent, but the
> `node` executable is not. You must provide a Node.js build for
> `aarch64-linux-android` (Bionic). Good sources: the
> [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile) project, a
> Termux/static build for `aarch64`, or any Node 18/20/22 build targetting
> Android ARM64. Place any companion `.so` files next to it (the script copies
> them into `app/src/main/assets/9router-runtime/lib/`). See
> [docs/ARCHITECTURE.md#node-binary-for-android](./docs/ARCHITECTURE.md#node-binary-for-android).
>
> If you can't provide a Node binary, set `ANDROID_NODE_URL`.

The script:
1. pins upstream 9Router (default commit `eb712ca` = v0.5.69),
2. runs `npm install` + `npm run build` to produce `.next/standalone`,
3. copies the bundle (+ `custom-server.js`, `public`, `.next/static`) into
   `app/src/main/assets/9router-runtime/bundle/`,
4. drops in the Node binary + shared libs,
5. writes `runtime-manifest.json` (upstream commit + node version).

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
3. `RouterRuntime.extractIfNeeded()` copies the packaged Node binary + 9Router
   bundle from assets into the app's private files dir (no root).
4. `InstanceManager` builds a fresh per-instance data dir and a `.env` from secrets
   generated by `SecretGenerator` + `SecureStorage` (Android Keystore).
5. `RouterProcess` spawns `node custom-server.js --port 20128` (env `PORT` /
   `HOSTNAME` are set; the standalone path uses `PORT`).
6. `HealthChecker` probes `http://127.0.0.1:20128/v1/models` until the server
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
- **ARM64 (arm64-v8a)** device (the embedded Node.js runtime is ARM64-only)
- No root, no Termux, no separate Node install

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
