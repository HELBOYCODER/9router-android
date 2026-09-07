# عیب‌یابی فارسی

## پیام Runtime not found

اسکریپت آماده‌سازی runtime اجرا نشده است:

```bash
./scripts/package_runtime.sh --node /path/to/node-aarch64-linux-android
```

بعد از آن دوباره APK را build کنید.

## Node اجرا نمی‌شود

Node معمولی Linux ARM64 که با glibc ساخته شده است، روی Android/Bionic قابل اتکا نیست. از nodejs-mobile، Termux ARM64 یا build اختصاصی Android استفاده کنید.

## Port conflict

اگر پورت 20128 اشغال باشد، از Settings پورت دیگری انتخاب کنید و Disconnect/Connect انجام دهید.

## LAN mode

LAN mode روی `0.0.0.0` bind می‌شود و دستگاه‌های شبکه محلی ممکن است API و داشبورد را ببینند. فقط در شبکه مورد اعتماد فعال کنید و API-key protection را روشن نگه دارید.

## توقف سرویس در پس‌زمینه

- برای برنامه Battery optimization را روی Unrestricted بگذارید.
- Notification permission را در Android 13+ تأیید کنید.
- Android می‌تواند حتی Foreground Service را در شرایط شدید متوقف کند؛ برنامه روی اجرای بعدی قابلیت auto-resume دارد.

## خطای Build

- JDK 17 استفاده کنید.
- Android SDK Platform 35 و Build Tools نصب باشد.
- `local.properties` باید `sdk.dir` درست داشته باشد.
- برای سیستم‌های کم‌حافظه از `--max-workers=1` استفاده کنید.
