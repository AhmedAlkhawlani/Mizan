package com.maknoon.mizan.saver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the notification's «إيقاف الحماية» action without starting the service again. */
class SaverActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SaverControl.ACTION_STOP) {
            SaverControl.stop(context)
        }
    }
}
