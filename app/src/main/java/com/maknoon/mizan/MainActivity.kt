package com.maknoon.mizan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.maknoon.mizan.app.AppLanguage
import com.maknoon.mizan.app.MizanApp
import com.maknoon.mizan.app.ShortcutTarget
import com.maknoon.mizan.app.shortcutTarget
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.monitoring.MonitoringControl
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import com.maknoon.mizan.sync.AppUsageSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val shortcutDestination = MutableStateFlow<String?>(null)

    /**
     * Applied before the activity's resources are created, so the first frame is already
     * Arabic and right-to-left rather than re-laying out after a language change.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            AppLanguage.wrapContext(newBase, AppLanguage.DEFAULT_LANGUAGE_TAG),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stoppedByShortcut = handleShortcutIntent(intent)
        if (savedInstanceState == null) {
            // A stop shortcut has just written "off"; resuming in the same breath would race
            // that write and could restart the service the operator asked to stop.
            if (!stoppedByShortcut) resumeMonitoringIfRequested()
            // The periodic registration is idempotent; the one-time run makes opening the app
            // refresh the per-app figures instead of waiting up to a quarter of an hour.
            AppUsageSyncScheduler.schedule(applicationContext)
            AppUsageSyncScheduler.syncNow(applicationContext)
        }
        setContent {
            MizanTheme {
                val destination by shortcutDestination.collectAsState()
                MizanApp(
                    shortcutDestination = destination,
                    onShortcutDestinationHandled = { shortcutDestination.value = null },
                )
            }
        }
    }

    /** A shortcut can arrive while the task is already in the foreground. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    /**
     * Returns true when the shortcut only stopped monitoring, because that must suppress the
     * automatic resume for this launch.
     */
    private fun handleShortcutIntent(intent: Intent?): Boolean {
        val host = intent?.data?.host
        // Consume the URI so rotating the screen does not replay the shortcut.
        intent?.data = null
        return when (val target = shortcutTarget(host)) {
            is ShortcutTarget.Destination -> {
                shortcutDestination.value = target.route
                false
            }

            ShortcutTarget.StopMonitoring -> {
                MonitoringControl.stop(this)
                true
            }

            null -> false
        }
    }

    /**
     * A persisted "on" means the user wants monitoring, even if the process and its service
     * died since the last time. Resuming from a starting activity is allowed on Android 12+,
     * whereas starting a foreground service from the background is not.
     */
    private fun resumeMonitoringIfRequested() {
        val appContext = applicationContext
        lifecycleScope.launch {
            val enabled = MizanSettings(appContext).monitoringEnabled.first()
            if (enabled && !MonitoringStateHolder.state.value.running) {
                MonitoringControl.start(appContext)
            }
        }
    }
}
