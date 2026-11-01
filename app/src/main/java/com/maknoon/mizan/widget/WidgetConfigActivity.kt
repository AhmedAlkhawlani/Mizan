package com.maknoon.mizan.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.maknoon.mizan.R
import com.maknoon.mizan.app.AppLanguage
import com.maknoon.mizan.core.data.widget.BudgetWidgetConfig
import com.maknoon.mizan.core.data.widget.SpeedWidgetConfig
import com.maknoon.mizan.core.data.widget.WidgetConfigStore
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Shared add/edit flow for both widgets: the launcher hands over an `appWidgetId`, the operator
 * picks what that instance shows, and [saveAndFinish] persists the choices before returning
 * `RESULT_OK`. Backing out writes nothing and keeps the launcher's default.
 */
abstract class WidgetConfigActivity : ComponentActivity() {

    protected var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    private var saving = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            AppLanguage.wrapContext(newBase, AppLanguage.DEFAULT_LANGUAGE_TAG),
        )
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Restored by the save path; anything else, including rotation and back, cancels.
        setResult(RESULT_CANCELED)
        showConfiguration()
    }

    protected abstract fun showConfiguration()

    protected fun glanceId() = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)

    protected fun saveAndFinish(save: suspend () -> Unit, refresh: suspend () -> Unit) {
        if (saving) return
        saving = true
        lifecycleScope.launch {
            try {
                save()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the sheet open so the choices can be retried instead of silently lost.
                Log.w(TAG, "saving widget configuration failed", e)
                saving = false
                Toast.makeText(
                    this@WidgetConfigActivity,
                    R.string.widget_config_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The choices are saved; failing to repaint an instance must not cancel the add.
                Log.w(TAG, "widget refresh after configuration failed", e)
            }
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }

    private companion object {
        const val TAG = "MizanWidgetConfig"
    }
}

class SpeedWidgetConfigActivity : WidgetConfigActivity() {

    override fun showConfiguration() {
        lifecycleScope.launch {
            val initial = try {
                WidgetConfigStore(this@SpeedWidgetConfigActivity).speed(appWidgetId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "reading speed widget config failed", e)
                SpeedWidgetConfig()
            }
            setContent {
                MizanTheme {
                    var showToday by rememberSaveable { mutableStateOf(initial.showToday) }
                    var showUpload by rememberSaveable { mutableStateOf(initial.showUpload) }
                    WidgetConfigScreen(
                        title = stringResource(R.string.widget_config_speed_title),
                        subtitle = stringResource(R.string.widget_config_speed_subtitle),
                        options = listOf(
                            WidgetConfigOption(
                                key = OPTION_TODAY,
                                label = stringResource(R.string.widget_config_show_today),
                                hint = stringResource(R.string.widget_config_show_today_hint),
                                checked = showToday,
                            ),
                            WidgetConfigOption(
                                key = OPTION_UPLOAD,
                                label = stringResource(R.string.widget_config_show_upload),
                                hint = stringResource(R.string.widget_config_show_upload_hint),
                                checked = showUpload,
                            ),
                        ),
                        onToggle = { key, checked ->
                            if (key == OPTION_TODAY) showToday = checked else showUpload = checked
                        },
                        onCancel = ::finish,
                        onSave = {
                            saveAndFinish(
                                save = {
                                    WidgetConfigStore(this@SpeedWidgetConfigActivity)
                                        .saveSpeed(appWidgetId, SpeedWidgetConfig(showToday, showUpload))
                                },
                                refresh = {
                                    SpeedWidget().update(this@SpeedWidgetConfigActivity, glanceId())
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "MizanWidgetConfig"
        const val OPTION_TODAY = "today"
        const val OPTION_UPLOAD = "upload"
    }
}

class BudgetWidgetConfigActivity : WidgetConfigActivity() {

    override fun showConfiguration() {
        lifecycleScope.launch {
            val initial = try {
                WidgetConfigStore(this@BudgetWidgetConfigActivity).budget(appWidgetId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "reading budget widget config failed", e)
                BudgetWidgetConfig()
            }
            setContent {
                MizanTheme {
                    var showTopApp by rememberSaveable { mutableStateOf(initial.showTopApp) }
                    var showRemaining by rememberSaveable { mutableStateOf(initial.showRemaining) }
                    WidgetConfigScreen(
                        title = stringResource(R.string.widget_config_budget_title),
                        subtitle = stringResource(R.string.widget_config_budget_subtitle),
                        options = listOf(
                            WidgetConfigOption(
                                key = OPTION_TOP_APP,
                                label = stringResource(R.string.widget_config_show_top_app),
                                hint = stringResource(R.string.widget_config_show_top_app_hint),
                                checked = showTopApp,
                            ),
                            WidgetConfigOption(
                                key = OPTION_REMAINING,
                                label = stringResource(R.string.widget_config_show_remaining),
                                hint = stringResource(R.string.widget_config_show_remaining_hint),
                                checked = showRemaining,
                            ),
                        ),
                        onToggle = { key, checked ->
                            if (key == OPTION_TOP_APP) showTopApp = checked else showRemaining = checked
                        },
                        onCancel = ::finish,
                        onSave = {
                            saveAndFinish(
                                save = {
                                    WidgetConfigStore(this@BudgetWidgetConfigActivity).saveBudget(
                                        appWidgetId,
                                        BudgetWidgetConfig(showTopApp, showRemaining),
                                    )
                                },
                                refresh = {
                                    BudgetWidget().update(this@BudgetWidgetConfigActivity, glanceId())
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "MizanWidgetConfig"
        const val OPTION_TOP_APP = "top_app"
        const val OPTION_REMAINING = "remaining"
    }
}

private data class WidgetConfigOption(
    val key: String,
    val label: String,
    val hint: String,
    val checked: Boolean,
)

@Composable
private fun WidgetConfigScreen(
    title: String,
    subtitle: String,
    options: List<WidgetConfigOption>,
    onToggle: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = option.checked,
                            role = Role.Switch,
                            onValueChange = { onToggle(option.key, it) },
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = option.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(checked = option.checked, onCheckedChange = null)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.widget_config_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) {
                    Text(text = stringResource(R.string.widget_config_save))
                }
            }
        }
    }
}
