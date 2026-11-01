package com.maknoon.mizan.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.maknoon.mizan.core.data.settings.MizanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Starts and stops [MonitoringService], and remembers the user's choice across reboots. */
object MonitoringControl {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        // startForegroundService on Android 8+, and the service promotes itself immediately,
        // so the platform's five-second deadline is never close.
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, MonitoringService::class.java),
        )
        persist(appContext, enabled = true)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, MonitoringService::class.java))
        persist(appContext, enabled = false)
    }

    /**
     * Written on the user's action rather than in the service lifecycle, because a service
     * killed by the system never gets to update a "stopped" flag and would resurrect itself
     * after the next boot.
     */
    private fun persist(context: Context, enabled: Boolean) {
        scope.launch { MizanSettings(context).setMonitoringEnabled(enabled) }
    }
}
