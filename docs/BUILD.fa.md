# راهنمای Build نسخه Termux

## پیش‌نیازهای گوشی

- Android 10 یا بالاتر
- Termux از F-Droid یا GitHub رسمی
- اجرای یک‌باره Termux
- تنظیم `allow-external-apps=true` در `~/.termux/termux.properties`
- فعال‌کردن مجوز **Run commands in Termux** برای برنامه 9Router

## Build APK

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

خروجی‌ها:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

## اجرای برنامه روی گوشی

1. APK را نصب کنید.
2. Termux را باز کنید تا نصب اولیه آن کامل شود.
3. در Termux این فایل را ویرایش کنید:

```bash
mkdir -p ~/.termux
printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties
```

4. Termux را ببندید و دوباره باز کنید.
5. در Android Settings، برای 9Router مجوز Run commands in Termux را فعال کنید.
6. برنامه 9Router را باز کنید و CONNECT را بزنید.

برنامه به‌صورت خودکار وجود `nodejs-lts`، `npm`، `git` و `curl` را بررسی و موارد ناقص را با `pkg install` نصب می‌کند، سپس upstream 9Router را build و start می‌کند.

## نکته

نسخه Termux باید از یک منبع سازگار نصب شود؛ نسخه‌های F-Droid و GitHub را با هم ترکیب نکنید. Android اجازه نصب یا اعطای permission را به‌صورت silent نمی‌دهد و این مرحله حتماً باید توسط کاربر انجام شود.
