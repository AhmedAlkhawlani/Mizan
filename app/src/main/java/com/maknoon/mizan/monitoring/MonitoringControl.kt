package com.maknoon.mizan.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts and stops [MonitoringService] from the UI. */
object MonitoringControl {

    fun start(context: Context) {
        // startForegroundService on Android 8+, and the service promotes itself immediately,
        // so the platform's five-second deadline is never close.
        ContextCompat.startForegroundService(
            context,
            Intent(context, MonitoringService::class.java),
        )
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MonitoringService::class.java))
    }
}
