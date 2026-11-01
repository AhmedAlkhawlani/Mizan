package com.maknoon.mizan

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.maknoon.mizan.app.AppLanguage
import com.maknoon.mizan.app.MizanApp
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.monitoring.MonitoringControl
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
        if (savedInstanceState == null) {
            resumeMonitoringIfRequested()
        }
        setContent {
            MizanTheme {
                MizanApp()
            }
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
