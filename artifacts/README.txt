These APKs were built from this repository with ./gradlew assembleDebug / assembleRelease.
They contain the runtime placeholder because an Android/Bionic ARM64 Node.js binary was not available in the build environment.
Run ../scripts/package_runtime.sh --node <aarch64-linux-android-node>, then rebuild for a functional self-hosted runtime.
