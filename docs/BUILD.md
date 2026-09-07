# Build instructions

This project builds with **Gradle 8.9** (wrapper included) and is meant to be opened
in **Android Studio**. The embedded 9Router runtime is assembled by a separate
host-side script because it requires Node.js and an Android ARM64 Node binary.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | Android Studio Ladybug+ bundles JBR 17/21; a standalone JDK 17 works too. |
| Android SDK | compileSdk 35, build-tools 35 | `ANDROID_HOME` / local.properties. |
| Node.js + npm | 18+ | Only needed to run `scripts/package_runtime.sh` on the build host. |
| Git | any | Used by the packaging script to fetch upstream 9Router. |
| Android device | ARM64, Android 10+ | For `installDebug`. |

---

## Step 1 — Assemble the embedded 9Router runtime (one-time)

The bundled runtime is **not** committed to git (it's large and arch-specific). Populate it:

```bash
cd 9router-android
./scripts/package_runtime.sh --node /path/to/node-aarch64-android
```

If you don't have a local Node binary, set a URL:

```bash
ANDROID_NODE_URL="https://…/node" ./scripts/package_runtime.sh
```

The script:
- clones upstream 9Router at the pinned commit,
- runs `npm install` + `npm run build`,
- copies the `.next/standalone` bundle + `custom-server.js` + `public` + `.next/static`
  into `app/src/main/assets/9router-runtime/bundle/`,
- installs the Node binary + shared libs,
- writes `runtime-manifest.json`.

Verify it worked:
```bash
ls app/src/main/assets/9router-runtime/bin/node
ls app/src/main/assets/9router-runtime/bundle/custom-server.js
```

> Windows: run the script from **Git Bash** or WSL, or use the Gradle build on a
> Linux/macOS machine / CI. `gradlew.bat` is available for compiling on Windows once
> the assets are already populated.

### Quick sanity check on a Linux x64 host (no device)

The JavaScript bundle is arch-independent, so you can validate the server locally:

```bash
(cd app/src/main/assets/9router-runtime/bundle && PORT=20128 HOSTNAME=127.0.0.1 node custom-server.js)
curl http://127.0.0.1:20128/v1/models
```

---

## Step 2 — Open in Android Studio

1. `File → Open` → select the `9router-android/` folder.
2. Let Gradle sync (it downloads AGP 8.7 / Kotlin 2.0 / dependencies).
3. Pick an **ARM64 device/emulator** (Android 10+).
4. Press **Run ▶**.

That's it. `CONNECT` will extract the runtime and start 9Router.

---

## Step 3 — CLI build (required for CI or headless)

Create `local.properties` (or set `ANDROID_HOME`):

```properties
sdk.dir=/path/to/Android/Sdk
```

Then:

```bash
# Debug APK
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned unless you configure signing)
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

### Release signing

Configure `/home/work/.openclaw/workspace/9router-android/app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KS_PASS")
            keyAlias = System.getenv("KS_ALIAS")
            keyPassword = System.getenv("KS_PASS")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // …
        }
    }
}
```

Then `KS_PASS=… KS_ALIAS=… ./gradlew assembleRelease`. Never commit the keystore or
its password.

### Universal (all-ABI) APK — optional

By default only `arm64-v8a` is built (the required ABI for the Node runtime). To also
emit a universal APK, enable the `splits` block in `app/build.gradle.kts`:

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a", "x86_64", "armeabi-v7a")
        isUniversalApk = true
    }
}
```

> A universal APK will be larger and only the ARM64 slice runs the embedded runtime.

---

## Build outputs

| Variant | Path |
|---------|------|
| Debug (ARM64) | `app/build/outputs/apk/debug/app-debug.apk` |
| Release (ARM64) | `app/build/outputs/apk/release/app-release.apk` |
| Universal (if enabled) | `app/build/outputs/apk/release/app-universal-release.apk` |

---

## Troubleshooting builds

See [docs/TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for:
- "Embedded 9Router runtime not found" (run the packaging script),
- AGP/JDK/compileSdk mismatches,
- Node binary is not aarch64,
- port conflicts,
- WebView / dashboard issues,
- background-kill & battery,
- notifications not appearing.
