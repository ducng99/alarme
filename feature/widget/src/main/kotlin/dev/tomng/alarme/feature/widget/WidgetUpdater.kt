package dev.tomng.alarme.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Pushes the current next alarm into every placed widget. */
object WidgetUpdater {

    suspend fun refresh(context: Context) {
        AlarmWidget().updateAll(context)
    }
}
