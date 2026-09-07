# 9Router Android — ProGuard / R8 rules.
# Keep the Node.js embedded runtime entry point names (not needed for minify of Java, but safe).
-keep class com.ninerouter.android.router.** { *; }
-keep class com.ninerouter.android.service.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Keep the application class (referenced from manifest).
-keep class com.ninerouter.android.NineRouterApp { *; }

# Keep notification/service metadata (Binder/PendingIntent targets must not be renamed).
-keep class com.ninerouter.android.service.RouterForegroundService { *; }
-keep class com.ninerouter.android.MainActivity { *; }

# androidx.security / EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# JUnit / tests only in test scope, no rules needed.
