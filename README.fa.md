# کلاینت اندروید 9Router

یک برنامه Native برای اندروید که نمونه Self-Hosted پروژه [9Router](https://9router.com/) را مستقیماً روی دستگاه اجرا می‌کند؛ بدون Root، بدون Termux و بدون نیاز به اجرای دستی Node.js یا Shell.

> **وضعیت مهم:** سورس اندروید و APKها با موفقیت build شده‌اند. برای اجرای واقعی 9Router داخل APK باید قبل از build نهایی، یک Node.js سازگار با Android/Bionic برای معماری ARM64 به runtime اضافه شود. یک Node معمولی Linux/glibc روی Android اجرا نمی‌شود.

## امکانات

- دکمه CONNECT / DISCONNECT
- Foreground Service برای اجرای پایدار در پس‌زمینه
- اجرای API سازگار با OpenAI در:
  - `http://127.0.0.1:20128/v1`
- داشبورد داخلی WebView در:
  - `http://127.0.0.1:20128/dashboard`
- تنظیم Host و Port
- حالت Localhost فقط به‌صورت پیش‌فرض
- حالت اختیاری LAN با هشدار امنیتی
- تولید Secretهای تصادفی برای هر instance
- نگهداری Secretها با Android Keystore و EncryptedSharedPreferences
- بررسی سلامت `/v1/models`
- Restart خودکار کنترل‌شده با Exponential Backoff
- Reinitialize کردن instance محلی
- نمایش، پاک‌سازی، کپی و Export لاگ‌ها
- مدیریت Providerها از داشبورد اصلی 9Router
- عدم قرار دادن API Key یا credential در سورس یا لاگ

## ساخت Runtime نهایی

از ریشه پروژه اجرا کنید:

```bash
./scripts/package_runtime.sh --node /path/to/node-aarch64-linux-android
```

فایل Node باید برای Android ARM64 و Bionic ساخته شده باشد؛ گزینه‌های ممکن:

- nodejs-mobile
- Node مربوط به Termux برای ARM64
- یک build اختصاصی با target `aarch64-linux-android`

سپس APK را بسازید:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## APKهای ساخته‌شده

- Debug: `artifacts/9router-android-debug.apk`
- Release unsigned: `artifacts/9router-android-release-unsigned.apk`

این APKها از نظر Android/Gradle build معتبر هستند، اما در محیط فعلی فقط runtime placeholder وجود داشت؛ بنابراین برای اجرای واقعی 9Router باید ابتدا `package_runtime.sh` اجرا و سپس APK دوباره ساخته شود.

## امنیت

- حالت پیش‌فرض فقط `127.0.0.1` است.
- فعال‌سازی `0.0.0.0` با هشدار صریح انجام می‌شود.
- Secretهای محلی با `SecureRandom` تولید می‌شوند.
- credentialهای Providerها توسط کاربر و در داشبورد upstream تنظیم می‌شوند.
- برنامه هیچ توکن Provider را برای عبور از quota یا محدودیت‌های سرویس تولید، harvest یا rotate نمی‌کند.
- لاگ‌ها قبل از ذخیره و Export sanitize می‌شوند.

## مستندات

- [English README](./README.md)
- [معماری و محدودیت‌ها](./docs/ARCHITECTURE.md)
- [راهنمای Build](./docs/BUILD.md)
- [عیب‌یابی](./docs/TROUBLESHOOTING.md)

## نسخه upstream

نسخه استفاده‌شده از upstream:

```text
eb712ca821f0ba6bc41043fbd14494c5af5daba5
9Router v0.5.69
```

منطق routing/provider upstream بازنویسی نشده است؛ Android فقط wrapper، lifecycle، runtime extraction، service، UI و security storage را اضافه می‌کند.
