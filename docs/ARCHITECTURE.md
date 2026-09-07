# Architecture — Termux runtime integration

## Decision

The first prototype attempted to spawn an embedded `node` executable. That is not a reliable Android architecture because upstream Node.js builds target host libc and the official nodejs-mobile package supplies `libnode.so`, not a standalone `node` executable.

The Android client now uses the official Termux `RUN_COMMAND` integration:

```text
9Router Android app
  └── RUN_COMMAND intent
       └── Termux bash
            ├── checks/install nodejs-lts, npm, git, curl
            ├── clones upstream 9Router
            ├── npm install
            ├── npm run build
            └── starts node custom-server.js
```

Termux is the Android/Bionic runtime. The app owns lifecycle, settings, health checks, notifications, and user-facing logs.

## Android permissions and limitations

The app declares:

```text
com.termux.permission.RUN_COMMAND
```

The user must grant this from Android Settings. Termux must also have:

```text
allow-external-apps=true
```

in `~/.termux/termux.properties`.

An Android application cannot silently install Termux, grant this permission, or bypass Android background-start restrictions. This is intentional platform security behavior.

## Bootstrap

`TermuxIntegration.kt` sends a background RUN_COMMAND intent containing a shell bootstrap. It:

1. checks `pkg`,
2. runs `pkg update`,
3. installs missing `nodejs-lts`, `git`, and `curl`,
4. clones upstream 9Router at the pinned commit,
5. runs `npm install --no-audit --no-fund`,
6. runs `npm run build`,
7. generates fresh `.env` secrets using Node crypto,
8. starts the standalone server with `nohup`,
9. writes bootstrap/server logs under `~/.9router/`.

The app then polls `/v1/models` until healthy and continues periodic health checks for crash detection.

## Upstream compatibility

- Repository: `https://github.com/decolua/9router`
- Commit: `eb712ca821f0ba6bc41043fbd14494c5af5daba5`
- Version: `v0.5.69`
- Routing/provider logic: unchanged
- Android-specific code: Termux bridge, foreground service, UI, security storage, lifecycle and health monitoring

## Known limitations

- First launch can take several minutes because dependencies are installed and Next.js is built on-device.
- Termux, not the Android app, owns the Node process.
- Termux must remain installed and allowed to run in the background.
- Battery optimization or aggressive OEM task killers can stop Termux.
- Provider credentials are configured by the user in the upstream dashboard.
- LAN mode exposes the local server to the local network and is disabled by default.
