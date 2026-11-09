package dev.tomng.alarme

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.feature.widget.WidgetRefreshKey
import dev.tomng.alarme.feature.widget.WidgetUpdater
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class AlarmeApplication : Application() {

    @Inject lateinit var alarmRepository: AlarmRepository

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // The widget shows the next alarm, so it follows the same source the list does.
        scope.launch {
            alarmRepository
                .observeNextAlarm()
                .map(WidgetRefreshKey::of)
                .distinctUntilChanged()
                .collect { WidgetUpdater.refresh(this@AlarmeApplication) }
        }
    }
}
