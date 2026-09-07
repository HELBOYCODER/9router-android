# راهنمای Build اندروید

## پیش‌نیازها

- Android Studio یا JDK 17
- Android SDK Platform 35
- Build Tools 35
- Node.js 18 یا بالاتر برای ساخت runtime
- Node.js مخصوص Android ARM64/Bionic برای اجرای واقعی داخل APK

## مرحله اول: آماده‌سازی runtime

```bash
./scripts/package_runtime.sh --node /path/to/node-aarch64-linux-android
```

اسکریپت نسخه upstream 9Router را روی commit زیر build می‌کند:

```text
eb712ca821f0ba6bc41043fbd14494c5af5daba5
```

سپس standalone build شامل `server.js`، `custom-server.js`، `.next/static`، `public` و dependencyهای runtime را در assets کپی می‌کند.

## مرحله دوم: ساخت APK

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

خروجی‌ها:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

## نصب روی دستگاه ARM64

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

حداقل Android نسخه 10 / API 29 است.

## Release امضاشده

برای انتشار واقعی، signing config را در `app/build.gradle.kts` اضافه کنید و password را فقط از environment variable بخوانید. Keystore، password و credentialها را commit نکنید.

## نکته مهم

APKهای موجود در `artifacts/` قبل از افزودن Node.js مخصوص Android ساخته شده‌اند. برای Functional APK باید runtime را با `package_runtime.sh` پر کنید و مجدداً build بگیرید.
