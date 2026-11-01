package com.maknoon.mizan

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maknoon.mizan.app.AppLanguage
import com.maknoon.mizan.app.MizanApp
import com.maknoon.mizan.core.designsystem.theme.MizanTheme

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
        setContent {
            MizanTheme {
                MizanApp()
            }
        }
    }
}
