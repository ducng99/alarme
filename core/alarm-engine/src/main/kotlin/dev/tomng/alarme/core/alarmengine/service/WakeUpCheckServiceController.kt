package dev.tomng.alarme.core.alarmengine.service

import android.app.Service
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.power.Wakelocks
import dev.tomng.alarme.core.alarmengine.ring.CheckVibrator
import dev.tomng.alarme.core.alarmengine.statemachine.RingEvent
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The wake-up check host's brain: a quiet foreground service that posts the 1002 prompt, vibrates,
 * and feeds [RingEvent.WakeUpCheckStarted] into the session — which arms the 100 s window. Alarms
 * that were deleted, disabled or skipped while the check waited never nag: the service stops
 * instead.
 */
@Singleton
class WakeUpCheckServiceController
@Inject
constructor(
    private val alarmRepository: AlarmRepository,
    private val notifications: AlarmNotifications,
    private val wakelocks: Wakelocks,
    private val vibrator: CheckVibrator,
    private val clock: AppClock,
    private val sessionStore: RingSessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var wakeLock: PowerManager.WakeLock? = null

    fun onStartCommand(service: Service, alarmId: Long) {
        if (alarmId == -1L) {
            service.stopSelf()
            return
        }
        notifications.ensureChannels()
        ServiceCompat.startForeground(
            service,
            AlarmNotifications.WAKE_UP_CHECK_NOTIFICATION_ID,
            notifications.wakeUpCheckNotification(alarmName = null).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )
        wakeLock = wakelocks.acquire(Wakelocks.WAKE_UP_CHECK, WAKELOCK_TIMEOUT)

        scope.launch {
            val alarm = alarmRepository.getAlarm(alarmId)
            if (alarm == null || !alarm.enabled || alarm.isSkipped) {
                service.stopSelf()
                return@launch
            }
            notifications.postWakeUpCheck(notifications.wakeUpCheckNotification(alarm.name))
            vibrator.start()
            sessionStore.submit(
                RingEvent.WakeUpCheckStarted(alarm = alarm, startTime = clock.now()),
            )
        }
    }

    fun onDestroy() {
        vibrator.stop()
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
    }

    companion object {
        /** The check window is 100 s; the wakelock outlasts it with margin. */
        val WAKELOCK_TIMEOUT: Duration = 10.minutes
    }
}
