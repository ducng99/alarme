package dev.tomng.alarme.core.alarmengine.ring

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.power.Wakelocks
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.scheduling.TriggerType
import dev.tomng.alarme.core.alarmengine.statemachine.BlockedRingReason
import dev.tomng.alarme.core.alarmengine.statemachine.RingEvent
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingTrigger
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmEvent
import dev.tomng.alarme.core.data.repository.AlarmEventType
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.RingEventRepository
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.model.scheduling.ScheduleIds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The ring service's brain (the [dev.tomng.alarme.core.alarmengine.service.AlarmService] shell is a
 * thin Hilt wrapper): posts the 1001 foreground notification, holds the 10-minute wakelock with
 * 9-minute renewal, logs the RING event, and starts the [RingHost].
 */
@Singleton
class AlarmServiceController
@Inject
constructor(
    private val alarmRepository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val ringEventRepository: RingEventRepository,
    private val notifications: AlarmNotifications,
    private val wakelocks: Wakelocks,
    private val ringHost: RingHost,
    private val audioOutputDetector: AudioOutputDetector,
    private val ringtoneResolver: RingtoneResolver,
    private val snoozeStore: dev.tomng.alarme.core.alarmengine.scheduling.SnoozeStore,
    private val clock: AppClock,
    private val sessionStore: RingSessionStore,
    private val effectExecutor: RingEffectExecutor,
    private val ringScreenStarter: RingScreenStarter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewalJob: Job? = null

    fun onStartCommand(
        service: Service,
        intent: Intent?,
        @Suppress("UNUSED_PARAMETER") flags: Int,
        @Suppress("UNUSED_PARAMETER") startId: Int,
    ): Int {
        val delivery = intent?.let(RingDelivery::fromIntent) ?: manualDeliveryOrNull(intent)
        if (delivery == null) {
            // Nothing restorable — stop instead of ringing a ghost.
            service.stopSelf()
            return Service.START_NOT_STICKY
        }

        notifications.ensureChannels()
        val notification = notifications.ringingNotification(alarmName = null)
        ServiceCompat.startForeground(
            service,
            AlarmNotifications.RINGING_NOTIFICATION_ID,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )

        holdCpuAwake()
        scope.launch { startRingHost(service, delivery) }
        return Service.START_REDELIVER_INTENT
    }

    fun onDestroy() {
        renewalJob?.cancel()
        renewalJob = null
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
        ringHost.stop()
    }

    private fun holdCpuAwake() {
        wakeLock = wakelocks.acquire(Wakelocks.ALARM_SERVICE, WAKELOCK_TIMEOUT)
        renewalJob?.cancel()
        renewalJob = scope.launch {
            while (coroutineContext.isActive) {
                delay(RENEWAL_INTERVAL)
                wakeLock?.acquire(WAKELOCK_TIMEOUT.inWholeMilliseconds)
            }
        }
    }

    private suspend fun startRingHost(service: Service, delivery: RingDelivery) {
        val alarm = alarmRepository.getAlarm(delivery.alarmId)
        if (alarm == null || !alarm.enabled) {
            // Deleted or disabled between arming and delivery — never ring a ghost.
            ringEventRepository.logEvent(delivery.blockedEvent(REASON_EMPTY))
            snoozeStore.clear()
            // Leave no half-open session behind: the ring screen would otherwise keep waiting on
            // a ring that never started.
            sessionStore.submit(RingEvent.RingBlocked(BlockedRingReason.INVALID_ALARM))
            service.stopSelf()
            return
        }
        // This ring consumes the durable snooze (the finish PI that started it) and a pending
        // skip-once (the occurrence after the skipped one rings normally again).
        snoozeStore.clear()
        if (alarm.isSkipped) alarmRepository.setSkipped(alarm.id, false)

        val settings = settingsRepository.settings.first()
        val effectiveRingtone = ringtoneResolver.resolve(alarm.ringtoneUri)
        val request =
            RingRequestBuilder.build(
                    alarm.copy(ringtoneUri = effectiveRingtone),
                    settings,
                    audioOutputDetector.hasExternalOutput(),
                )
                .copy(scheduleId = delivery.scheduleId)

        ringEventRepository.logEvent(
            AlarmEvent(
                id = 0L,
                event = AlarmEventType.RING,
                ringRootId = delivery.scheduleId.orEmpty(),
                timestamp = clock.now(),
                triggerType = delivery.triggerType.name,
                missionsJson = null,
                alarmName = alarm.name,
                alarmEmoji = alarm.emoji,
                scheduleId = delivery.scheduleId.orEmpty(),
            ),
        )
        notifications.postRinging(alarmName = alarm.name)
        // The full-screen intent is suppressed while the user is in the app; ask for the screen
        // too.
        ringScreenStarter.start()

        // The audio start now flows through the session store: priming the request, then
        // feeding the ring event, lets the executor (and the ring screen) see one session.
        effectExecutor.prime(request)
        sessionStore.submit(
            RingEvent.AlarmRingRequest(
                alarm = alarm,
                scheduleId =
                    ScheduleIds.parse(delivery.scheduleId.orEmpty())
                        ?: synthesizedScheduleId(alarm.id),
                trigger = delivery.triggerType.toRingTrigger(),
                ringEventId = delivery.alarmTimeMillis,
                startedAt = clock.now(),
                autoDismiss = settings.autoDismiss.toMillis()?.milliseconds,
                snoozeCount = delivery.snoozeCount,
                muteDuringMission = settings.muteDuringMission,
            ),
        )
    }

    /**
     * A manual start (a missed wake-up check re-ringing) carries only the alarm id: ring it now,
     * ungated by a schedule id, as a fresh normal ring.
     */
    private fun manualDeliveryOrNull(intent: Intent?): RingDelivery? {
        if (
            intent?.getStringExtra(AlarmServiceIntents.EXTRA_START_MODE) !=
                AlarmServiceIntents.START_MODE_MANUAL
        ) {
            return null
        }
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return null
        return RingDelivery(
            alarmId = alarmId,
            scheduleId = null,
            alarmTimeMillis = clock.now().toEpochMilliseconds(),
            triggerType = TriggerType.NORMAL,
        )
    }

    private fun synthesizedScheduleId(alarmId: Long) =
        dev.tomng.alarme.core.model.scheduling.ScheduleId(
            deviceId = "ring",
            alarmId = alarmId,
            alarmTime = "ring",
            nowTime = "ring",
        )

    private fun TriggerType.toRingTrigger(): RingTrigger =
        when (this) {
            TriggerType.SNOOZE -> RingTrigger.SNOOZE
            else -> RingTrigger.SCHEDULED
        }

    private fun RingDelivery.blockedEvent(reason: String) =
        AlarmEvent(
            id = 0L,
            event = AlarmEventType.BLOCKED,
            ringRootId = scheduleId.orEmpty(),
            timestamp = clock.now(),
            triggerType = reason,
            missionsJson = null,
            alarmName = "",
            alarmEmoji = "",
            scheduleId = scheduleId.orEmpty(),
        )

    companion object {
        /** 10-minute wakelock, renewed every 9 minutes. */
        val WAKELOCK_TIMEOUT: Duration = 10.minutes
        val RENEWAL_INTERVAL: Duration = 9.minutes
        private const val REASON_EMPTY = "alarm_empty_in_service"
    }
}
