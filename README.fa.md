# کلاینت اندروید 9Router با Termux

این برنامه یک نمونه Self-Hosted از [9Router](https://9router.com/) را از طریق integration رسمی Termux روی گوشی اجرا و مدیریت می‌کند.

## روند اجرا

1. Termux را از F-Droid یا GitHub رسمی نصب کنید.
2. Termux را یک‌بار باز کنید.
3. در `~/.termux/termux.properties` قرار دهید:

```text
allow-external-apps=true
```

4. در تنظیمات Android برای برنامه 9Router مجوز **Run commands in Termux** را فعال کنید.
5. در برنامه 9Router روی **CONNECT** بزنید.

برنامه بررسی می‌کند که موارد زیر نصب هستند یا نه:

- `pkg`
- `nodejs-lts`
- `npm`
- `git`
- `curl`

اگر چیزی وجود نداشته باشد، bootstrap داخل Termux آن را نصب می‌کند. سپس:

- نسخه upstream 9Router را clone می‌کند.
- commit مشخص‌شده را checkout می‌کند.
- `npm install` را اجرا می‌کند.
- `npm run build` را اجرا می‌کند.
- Secretهای محلی را با Node crypto می‌سازد.
- سرور را روی پورت تنظیم‌شده اجرا می‌کند.
- برنامه با `/v1/models` سلامت server را بررسی می‌کند.

## Endpointهای پیش‌فرض

```text
API:       http://127.0.0.1:20128/v1
Dashboard: http://127.0.0.1:20128/dashboard
```

## نکته امنیتی Android

برنامه‌های Android نمی‌توانند بدون اجازه کاربر:

- Termux را نصب کنند.
- مجوز Run commands in Termux را فعال کنند.
- اجرای فرمان خارجی را دور بزنند.

این محدودیت عمداً توسط Android اعمال می‌شود. بنابراین فقط نصب Termux و فعال‌کردن مجوز یک‌بار توسط کاربر لازم است؛ بعد از آن نصب prerequisiteها و اجرای 9Router خودکار انجام می‌شود.

## امکانات

- CONNECT / DISCONNECT
- Foreground Service
- Health Check
- Restart خودکار کنترل‌شده
- Reinitialize instance
- تنظیم Port و Host
- Localhost-only به‌صورت پیش‌فرض
- LAN mode با هشدار امنیتی
- Secretهای تصادفی و Android Keystore
- Log viewer، Copy و Export
- داشبورد داخلی WebView
- Provider screen برای تنظیم Providerها از داشبورد upstream

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

نسخه upstream استفاده‌شده:

```text
eb712ca821f0ba6bc41043fbd14494c5af5daba5
9Router v0.5.69
```

### نصب سریع در Termux با یک دستور (مشابه Hermes)

اگر ترجیح می‌دهید ۹روتر را مستقیماً از محیط خط فرمان Termux راه بیندازید و همیشه ۲۴/۷ فعال باشد:

```bash
pkg install -y curl
curl -fsSL https://raw.githubusercontent.com/3krbkbkrbrbg/9router-android/main/scripts/termux-install.sh | bash
```

پس از پایان، داشبورد روی `http://127.0.0.1:20128/dashboard` بالا می‌آید و سرویس از طریق `termux-services` در پس‌زمینه زنده می‌ماند.

## مستندات

- [English README](./README.md)
- [راهنمای فارسی Build](./docs/BUILD.fa.md)
- [راهنمای Build انگلیسی](./docs/BUILD.md)
- [عیب‌یابی فارسی](./docs/TROUBLESHOOTING.fa.md)
- [معماری انگلیسی](./docs/ARCHITECTURE.md)
