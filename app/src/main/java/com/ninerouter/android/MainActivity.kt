package com.ninerouter.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ninerouter.android.service.RouterForegroundService
import com.ninerouter.android.ui.DashboardActivity
import com.ninerouter.android.ui.MainScreen
import com.ninerouter.android.ui.theme.NineRouterTheme
import com.ninerouter.android.viewmodel.RouterViewModel

class MainActivity : ComponentActivity() {

    private val vm: RouterViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; service still runs without it https://developer.android.com/develop/ui/views/notifications/notification-permission */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleLaunchIntent(intent)

        setContent {
            NineRouterTheme {
                MainScreen(
                    vm = vm,
                    onOpenDashboard = { openDashboard() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action == RouterForegroundService.ACTION_OPEN_DASHBOARD) {
            // Defer so the Composition is up; dashboard URL comes from live state.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.post { openDashboard() }
            } else {
                openDashboard()
            }
        }
    }

    private fun openDashboard() {
        val url = with(vm.state.value) {
            if (dashboardUrl.isBlank()) "http://127.0.0.1:20128/dashboard" else dashboardUrl
        }
        startActivity(DashboardActivity.newIntent(this, url))
    }
}
