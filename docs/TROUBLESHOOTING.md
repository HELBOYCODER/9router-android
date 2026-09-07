# Troubleshooting

## Build-time

### "Embedded 9Router runtime not found"
The app shows `ERROR` / `runtimePresent = false` because `app/src/main/assets/9router-runtime/`
doesn't contain the bundle + Node binary.
**Fix:** run `./scripts/package_runtime.sh --node <aarch64-node>` from the repo root,
then rebuild.

### `./gradlew` fails to run / download Gradle
- Ensure you have a JDK 17+ (`java -version`).
- The Gradle wrapper (8.9) downloads itself on first use; keep network on.
- If the wrapper jar is missing, regenerate with `./gradlew wrapper` (or let Android
  Studio recreate it).

### "Could not determine java version" / AGP mismatch
- Use JDK 17 (AGP 8.7 requires it). In Android Studio, set
  `Settings → Build Tools → Gradle → Gradle JDK` to 17 or the bundled JBR.

### `node` binary is not aarch64
The packaging script checks `file` output. If you supplied a glibc Node for
`linux-arm64`, it won't run on Android (Bionic). Use a build for
**`aarch64-linux-android`** (nodejs-mobile / Termux arm64). See ARCHITECTURE.md.

### Native libs not found (`libc++_shared.so` missing)
The Node binary may need shared libraries. Place them next to your Node binary and
re-run the packaging script — it copies `*.so` into `assets/.../lib/`. The app sets
`LD_LIBRARY_PATH` to that folder.

---

## Runtime / device

### Pressing CONNECT shows an error quickly
Look in **Logs** (in-app). Common causes:
- No runtime packaged (above).
- **Port in use**: another process listens on 20128. Change the port in Settings
  (Disconnect → Connect to apply).
- Node can't execute (arch mismatch, or the `.so` libs missing).

### "Port conflict detected"
The `node` server logged `EADDRINUSE`. Change `Settings → Port`, or free the port.

### The server starts but `/v1/models` times out
- Check Logs for startup errors. 
- If `sql.js` WASM fails to load, the database layer errors. Re-run
  `package_runtime.sh` (ensure the `.wasm` is present) and try
  `Settings → Reinitialize Local Instance`.
- On a slow device the 45s health window may expire; retry.

### Dashboard (WebView) is blank
- Make sure 9Router is CONNECTED and the port in the URL matches.
- JavaScript is enabled by the app; if you see a white screen, try
  `Settings → Open dashboard in external browser`.
- Log in with the **initial password** (generated at first run, shown/rotated on
  reinit). It is stored encrypted, not displayed in plaintext logs.

### Notification doesn't show
- On Android 13+ grant **Notifications** permission when prompted (or
  `Settings → Apps → 9Router → Notifications`).
- If the app was closed with CONNECT on, the service is `START_STICKY` and the app
  auto-resumes it on next launch; grant permission beforehand.

### App is killed in background / CONNECT stops
- Disable battery optimization for 9Router (or add to the "unrestricted" list).
- Avoid aggressive task-killers.
- Even so, the service restarts on next app launch (auto-resume) and auto-recovery
  kicks in if CONNECT was left on.

### LAN mode: other devices can't reach it
- Confirm LAN mode is ON (Settings shows "LAN" and bind is `0.0.0.0`).
- Ensure the device and client are on the **same subnet** and the device's firewall/AP
  allows inbound to the port.
- Use the displayed LAN IP (from `NetworkUtils.localIpv4Address`). If it shows
  `127.0.0.1`, the device may be offline (no IPv4) — connect to Wi-Fi/network first.

---

## Security / logs

### I see ` <redacted>` in logs
That's expected — the sanitizer is working. It strips bearer tokens, `sk-…`, API
keys, passwords, and `SECRET=` values before anything is displayed or exported.

### Where are my secrets stored?
In `EncryptedSharedPreferences` backed by the Android Keystore (file
`secure_prefs`). They are never in source, git, or plaintext logs. `Reinitialize`
rotates them.

### Can I disable the API key requirement?
Yes — `Settings → Require API key on /v1` (default off, matching upstream). If off,
local clients may connect without a key. The generated endpoint key is shown in the
UI and copied over for the client.

---

## Provider connections

- Configure providers inside the **dashboard** (WebView or external browser) using
  accounts you own.
- This app never auto-generates/refreshes/rotates third-party tokens and never
  bypasses provider quotas or authentication. If a provider stops responding,
  credentials may need re-authing in the dashboard — that is normal and by design.
