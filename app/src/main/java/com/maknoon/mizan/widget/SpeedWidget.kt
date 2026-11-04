package com.maknoon.mizan.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.maknoon.mizan.MainActivity
import com.maknoon.mizan.R
import com.maknoon.mizan.core.data.widget.SpeedWidgetConfig
import com.maknoon.mizan.core.data.widget.WidgetConfigStore
import com.maknoon.mizan.core.designsystem.theme.MizanPalette
import com.maknoon.mizan.core.domain.format.isolateLtr
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import com.maknoon.mizan.ui.dashboard.DashboardUiState
import com.maknoon.mizan.ui.dashboard.toDashboardUiState
import kotlinx.coroutines.CancellationException

/**
 * The 2×1 home-screen widget: Mizan's live speeds and today's total, updated by the monitor.
 *
 * A widget cannot stream like the dashboard can, so the foreground service pushes a redraw
 * every [REFRESH_INTERVAL_SECONDS] seconds while it runs. When monitoring is off the widget
 * says so instead of freezing on the last speed, and a tap always opens the app.
 */
class SpeedWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = MonitoringStateHolder.state.value.toDashboardUiState()
        val config = loadConfig(context, id)
        provideContent { SpeedWidgetContent(state, config) }
    }

    private suspend fun loadConfig(context: Context, id: GlanceId): SpeedWidgetConfig = try {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        WidgetConfigStore(context).speed(appWidgetId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "speed widget config read failed, using defaults", e)
        SpeedWidgetConfig()
    }

    companion object {
        /** Redraw cadence for widgets only; the 1-second sampling stays in the service. */
        const val REFRESH_INTERVAL_SECONDS = 15L
        private const val TAG = "MizanWidget"

        suspend fun refresh(context: Context) {
            SpeedWidget().updateAll(context)
        }
    }
}

@Composable
private fun SpeedWidgetContent(state: DashboardUiState, config: SpeedWidgetConfig) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>())
            .background(Color(0xFF0D131A))
            .cornerRadius(20.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            when {
                !state.running -> Text(
                    text = context.getString(R.string.widget_speed_stopped),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF8A94A6)),
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )

                state.speedAvailable -> {
                    Text(
                        text = context.getString(
                            R.string.widget_speed_down,
                            isolateLtr(state.downSpeed),
                        ),
                        style = TextStyle(
                            color = ColorProvider(MizanPalette.DataDown),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    if (config.showUpload) {
                        Text(
                            text = context.getString(
                                R.string.widget_speed_up,
                                isolateLtr(state.upSpeed),
                            ),
                            style = TextStyle(
                                color = ColorProvider(MizanPalette.DataUp),
                                fontSize = 9.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }

                else -> Text(
                    text = context.getString(R.string.widget_speed_unknown),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF8A94A6)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
        if (config.showToday) {
            Column(
                modifier = GlanceModifier.padding(start = 8.dp),
                horizontalAlignment = Alignment.Horizontal.End,
            ) {
                Text(
                    text = context.getString(R.string.widget_today),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF8A94A6)),
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = isolateLtr(state.todayTotal),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFE5E7EB)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

class SpeedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpeedWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetConfigStore.deleteInBackground(context, appWidgetIds)
    }
}
