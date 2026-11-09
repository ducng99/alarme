package dev.tomng.alarme.feature.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import kotlinx.coroutines.flow.first

/** The home-screen widget: the next alarm's time, its label and a live countdown. */
class AlarmWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadContent(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        provideContent { WidgetUi(content = content, launch = launch) }
    }

    private suspend fun loadContent(context: Context): WidgetContent {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val alarm = entryPoint.alarmRepository().observeNextAlarm().first()
        val clock = entryPoint.clock()
        return WidgetContentMapper.map(
            alarm = alarm,
            now = clock.now(),
            zone = clock.zone(),
            // The Chronometer counts from the uptime clock, never from the wall clock.
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        )
    }
}

/** Hilt access for the widget, which the launcher creates rather than the graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun alarmRepository(): AlarmRepository

    fun clock(): AppClock
}

@Composable
internal fun WidgetUi(content: WidgetContent, launch: Intent?) {
    GlanceTheme {
        Column(
            modifier =
                GlanceModifier.fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .padding(12.dp)
                    .then(
                        if (launch != null) GlanceModifier.clickable(actionStartActivity(launch))
                        else GlanceModifier,
                    ),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            if (content.hasAlarm) {
                Text(
                    text = content.timeText,
                    style =
                        TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface,
                        ),
                )
                if (content.label.isNotBlank()) {
                    Text(
                        text = content.label,
                        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                    )
                }
                content.countdownBaseMillis?.let { base ->
                    AndroidRemoteViews(remoteViews = countdown(LocalContext.current, base))
                }
            } else {
                val context = LocalContext.current
                Text(
                    text = context.getString(R.string.widget_no_alarm),
                    style =
                        TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurface,
                        ),
                )
                Text(
                    text = context.getString(R.string.widget_no_alarm_hint),
                    style =
                        TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

/** A self-ticking countdown: the Chronometer counts down to the alarm without widget updates. */
private fun countdown(context: Context, baseMillis: Long): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometerCountDown(R.id.widget_countdown, true)
        setChronometer(R.id.widget_countdown, baseMillis, null, true)
    }

/** The launcher's entry point for the widget. */
class AlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlarmWidget()
}
