package com.maknoon.mizan.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the notification's pause action.
 *
 * A receiver is used instead of a service intent because stopping must work while the app is
 * in the background, where `startService` is refused on Android 8 and later.
 */
class MonitoringActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MonitoringService.ACTION_STOP) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
