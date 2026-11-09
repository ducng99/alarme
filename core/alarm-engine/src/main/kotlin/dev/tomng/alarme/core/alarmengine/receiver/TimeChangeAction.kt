package dev.tomng.alarme.core.alarmengine.receiver

import android.content.Intent
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.TimeChangeGuard
import dev.tomng.alarme.core.common.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * TIME_SET / TIMEZONE_CHANGED: the wall clock moved so every computed occurrence is invalid —
 * re-arm once per broadcast pair via [TimeChangeGuard].
 */
class TimeChangeAction
@Inject
constructor(
    private val guard: TimeChangeGuard,
    private val scheduler: AlarmScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun onTimeChanged(action: String?) {
        if (action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED) return
        if (!guard.shouldAct()) return
        scope.launch { scheduler.rearm(RescheduleReason.TIME_CHANGED) }
    }
}
