package com.ninerouter.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ninerouter.android.MainActivity
import com.ninerouter.android.NineRouterApp
import com.ninerouter.android.R
import com.ninerouter.android.model.ConnectionStatus
import com.ninerouter.android.model.RouterStatus
import com.ninerouter.android.router.RouterManager
import com.ninerouter.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that hosts the embedded 9Router instance.
 *
 * It does not implement routing/provider logic itself; it delegates to
 * [RouterManager]. While the user has CONNECT enabled the service stays in the
 * foreground, shows an ongoing notification (Local API + status), and exposes
 * Disconnect / Restart / Open Dashboard actions.
 */
class RouterForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "nine_router_service"
        const val NOTIFICATION_ID = 20128

        const val ACTION_START = "com.ninerouter.android.action.START"
        const val ACTION_STOP = "com.ninerouter.android.action.STOP"
        const val ACTION_RESTART = "com.ninerouter.android.action.RESTART"
        const val ACTION_REINIT = "com.ninerouter.android.action.REINIT"
        const val ACTION_OPEN_DASHBOARD = "com.ninerouter.android.action.OPEN_DASHBOARD"

        fun start(context: Context) {
            val i = Intent(context, RouterForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, RouterForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun restart(context: Context) {
            val i = Intent(context, RouterForegroundService::class.java).setAction(ACTION_RESTART)
            ContextCompat.startForegroundService(context, i)
        }

        fun reinit(context: Context) {
            val i = Intent(context, RouterForegroundService::class.java).setAction(ACTION_REINIT)
            ContextCompat.startForegroundService(context, i)
        }

        // Persist the running flag so the Application can recover the foreground
        // service if the OS killed our process while CONNECT was enabled.
        private const val PREFS = "router_service_state"
        private const val KEY_RUNNING = "was_running"

        fun wasRunning(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false)

        fun setRunning(context: Context, running: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_RUNNING, running).apply()
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        RouterManager.initialize(this)
        // Keep the notification in sync with the router state for the whole lifetime.
        startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // On a re-delivery (START_STICKY) with a null intent, resume if we were running.
        if (intent == null) {
            if (RouterManager.state.value.isRunning) {
                startAsForeground()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                setRunning(this, true)
                startAsForeground()
                RouterManager.start(this)
            }
            ACTION_RESTART -> {
                startAsForeground()
                RouterManager.restart()
            }
            ACTION_REINIT -> {
                startAsForeground()
                RouterManager.reinitialize(this)
            }
            ACTION_STOP -> {
                setRunning(this, false)
                RouterManager.stop()
                collectLatestNow() // synchronize notification to stopped
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> { /* ignore */ }
        }
        return START_STICKY
    }

    private fun startObserving() {
        if (collectorJob?.isActive == true) return
        collectorJob = serviceScope.launch {
            RouterManager.state.collectLatest { state ->
                updateNotification(state)
            }
        }
    }

    private fun collectLatestNow() {
        updateNotification(RouterManager.state.value)
    }

    private fun startAsForeground() {
        val notification = buildNotification(RouterManager.state.value)
        // Foreground service *types* (dataSync/specialUse) are enforced only from
        // Android 14 (API 34). Passing a type on 29–33 can be rejected by the OS,
        // so we only specify a type on API 34+ and use the 2-arg overload elsewhere.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: RouterStatus) {
        if (!state.isRunning) return
        val notification = buildNotification(state)
        val nm = NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: RouterStatus): Notification {
        val statusText = when (state.status) {
            ConnectionStatus.CONNECTED -> "Connected"
            ConnectionStatus.CONNECTING -> "Connecting…"
            ConnectionStatus.RESTARTING -> "Restarting…"
            else -> "Offline"
        }
        val content = "Local API: ${state.host}:${state.port}\nStatus: $statusText"

        val disconnectPi = servicePendingIntent(1, ACTION_STOP)
        val restartPi = servicePendingIntent(2, ACTION_RESTART)
        val dashboardPi = activityPendingIntent(3, ACTION_OPEN_DASHBOARD)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.action_disconnect), disconnectPi)
            .addAction(0, getString(R.string.action_restart), restartPi)
            .addAction(0, getString(R.string.action_open_dashboard), dashboardPi)
            .build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, RouterForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun activityPendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_router_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_router_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        serviceScope.cancel()
        AppLogger.i("RouterForegroundService", "Service destroyed")
        super.onDestroy()
    }
}
