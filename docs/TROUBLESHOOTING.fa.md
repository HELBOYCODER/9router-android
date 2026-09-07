# عیب‌یابی نسخه Termux

## Termux نصب نیست

Termux را از F-Droid یا GitHub رسمی نصب کنید، یک‌بار باز کنید و دوباره CONNECT بزنید.

## مجوز Run commands in Termux وجود ندارد

به مسیر زیر بروید:

```text
Android Settings → Apps → 9Router → Permissions → Additional permissions → Run commands in Termux
```

این مجوز باید توسط کاربر فعال شود و برنامه نمی‌تواند آن را silent فعال کند.

## allow-external-apps تنظیم نشده

در Termux اجرا کنید:

```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
```

سپس Termux را کاملاً ببندید و دوباره باز کنید.

## prerequisiteها نصب نیستند

برنامه در اولین CONNECT به‌صورت خودکار این موارد را نصب می‌کند:

```text
nodejs-lts
npm
git
curl
```

لاگ Termux در مسیر زیر ذخیره می‌شود:

```text
~/.9router/bootstrap.log
~/.9router/server.log
```

## Build خیلی طول می‌کشد

اولین اجرا شامل clone، نصب npm dependencyها و `next build` است و ممکن است چند دقیقه زمان ببرد. اتصال اینترنت و فضای خالی کافی لازم است.

## Port conflict

از Settings پورت را تغییر دهید. مقدار پیش‌فرض 20128 است.

## Termux در پس‌زمینه متوقف می‌شود

- Battery optimization را برای Termux و 9Router غیرفعال کنید.
- Notificationهای Termux را فعال بگذارید.
- از task killerهای گوشی استفاده نکنید.
- برای Android 10+ ممکن است Draw over other apps برای Termux لازم باشد.

## LAN mode

LAN mode روی `0.0.0.0` bind می‌شود. فقط روی شبکه مورد اعتماد فعال کنید و API-key protection را روشن نگه دارید.
