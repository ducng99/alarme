package dev.tomng.alarme.core.alarmengine.ring

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.SnoozeStore
import dev.tomng.alarme.core.alarmengine.service.AlarmService
import dev.tomng.alarme.core.alarmengine.service.WakeUpCheckService
import dev.tomng.alarme.core.alarmengine.statemachine.RingEffect
import dev.tomng.alarme.core.alarmengine.statemachine.RingEffectListener
import dev.tomng.alarme.core.alarmengine.statemachine.RingEvent
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.model.alarm.Alarm
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs the reducer's commands on the main thread: audio start/stop against the request primed by
 * the ring service, service shutdown on dismiss, the ring-loop timers (auto-dismiss, snooze finish,
 * mission/emergency countdowns, the 100 s wake-up-check window) which feed their expiry back into
 * the session as events, and the wake-up-check arming (request code 100).
 */
@Singleton
class RingEffectExecutor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val ringHost: RingHost,
    private val clock: AppClock,
    private val store: RingSessionStore,
    private val scheduler: AlarmScheduler,
    private val snoozeStore: SnoozeStore,
    private val notifications: AlarmNotifications,
    private val alarmRepository: AlarmRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : RingEffectListener {

    private val handler = Handler(Looper.getMainLooper())
    private var primedRequest: RingRequest? = null
    private var autoDismissRunnable: Runnable? = null
    private var countdown: Countdown? = null

    init {
        store.addEffectListener(this)
    }

    /** The service primes the request it built before submitting the ring event. */
    fun prime(request: RingRequest) {
        primedRequest = request
    }

    override fun onEffect(effect: RingEffect) {
        handler.post { handle(effect) }
    }

    private fun handle(effect: RingEffect) {
        when (effect) {
            is RingEffect.Ring,
            is RingEffect.RestoreRing -> startPrimedHost()

            is RingEffect.ReRing -> startRingService(alarm = effect.alarm)

            is RingEffect.Dismiss -> dismissRing(effect.alarm)

            is RingEffect.StartAutoDismissTimer -> scheduleAutoDismiss(effect.duration)
            RingEffect.StopAutoDismissTimer -> cancelAutoDismiss()

            is RingEffect.Snooze -> startDurableSnooze(effect)

            is RingEffect.StartMissionTimer ->
                startCountdown(
                    duration = effect.duration,
                    onTick = { remaining -> store.submit(RingEvent.OnMissionTimerTick(remaining)) },
                    onExpire = { store.submit(RingEvent.OnMissionTimeout) },
                )

            is RingEffect.ExtendMissionTime -> countdown?.extendBy(effect.duration)

            RingEffect.StopMissionTimer -> cancelCountdown()

            is RingEffect.StartEmergencyTimer ->
                startCountdown(
                    duration = effect.duration,
                    onTick = { remaining ->
                        store.submit(RingEvent.OnEmergencyTimerTick(remaining, clock.now()))
                    },
                    onExpire = {
                        store.submit(RingEvent.OnEmergencyTimerTick(Duration.ZERO, clock.now()))
                    },
                )

            is RingEffect.StartWakeUpCheckTimer ->
                startCountdown(
                    duration = effect.duration,
                    onTick = {},
                    onExpire = { store.submit(RingEvent.OnWakeUpCheckFailed) },
                )

            RingEffect.StopWakeUpCheckTimer -> cancelCountdown()

            is RingEffect.ScheduleWakeUpCheck ->
                scheduler.armWakeUpCheck(effect.alarm.id, effect.at)

            RingEffect.CancelWakeUpCheck -> {
                scheduler.cancelWakeUpCheck()
                context.stopService(Intent(context, WakeUpCheckService::class.java))
            }

            is RingEffect.Reschedule -> rearm(effect.reason)

            // A completed ring zeroes the persisted snooze counters.
            RingEffect.ResetSnooze -> {
                snoozeStore.clear()
                scheduler.cancelSnooze()
            }

            // The ring itself re-arms the next occurrence only via dismiss/reschedule.
            else -> Unit
        }
    }

    /** A missed check rings the whole alarm again — service, notification, missions and all. */
    private fun startRingService(alarm: Alarm) {
        val intent = AlarmServiceIntents.manualStartIntent(context, alarm.id)
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) {
            // The check service is still foreground; a refused start is retried on the next arm.
            primedRequest?.let(ringHost::start)
        }
    }

    private fun startPrimedHost() {
        primedRequest?.let(ringHost::start)
    }

    private fun dismissRing(alarm: Alarm) {
        cancelAutoDismiss()
        cancelCountdown()
        ringHost.stop()
        context.stopService(Intent(context, AlarmService::class.java))
        // A one-shot alarm has no next occurrence: it turns itself off once it has rung.
        scope.launch {
            if (!alarm.daysOfWeek.isRepeating) alarmRepository.setEnabled(alarm.id, false)
            scheduler.rearm(RescheduleReason.DISMISS)
        }
    }

    private fun rearm(reason: RescheduleReason) {
        scope.launch { scheduler.rearm(reason) }
    }

    /**
     * The snooze finish is a real alarm-manager PI (`setAlarmClock`), not a handler timer: it
     * survives process death and shows in the status bar. The record feeds the boot restore; the
     * next normal occurrence is re-armed immediately so a crash mid-snooze can't lose tomorrow.
     */
    private fun startDurableSnooze(effect: RingEffect.Snooze) {
        ringHost.stop()
        snoozeStore.save(
            dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord(
                alarmId = effect.alarm.id,
                finishAtMillis = effect.finishAt.toEpochMilliseconds(),
                count = effect.count,
            ),
        )
        scheduler.armSnooze(
            alarmId = effect.alarm.id,
            scheduleId = primedRequest?.scheduleId,
            at = effect.finishAt,
            count = effect.count,
        )
        notifications.postRinging(
            notifications.snoozedNotification(
                alarmName = effect.alarm.name,
                until = java.time.Instant.ofEpochMilli(effect.finishAt.toEpochMilliseconds()),
            ),
        )
        rearm(RescheduleReason.SNOOZE_ALARM)
    }

    private fun scheduleAutoDismiss(duration: Duration) {
        cancelAutoDismiss()
        val runnable = Runnable { store.submit(RingEvent.AutoDismissTimeout(clock.now())) }
        autoDismissRunnable = runnable
        handler.postDelayed(runnable, duration.inWholeMilliseconds)
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let(handler::removeCallbacks)
        autoDismissRunnable = null
    }

    /**
     * A one-second countdown loop. The deadline is wall-clock based so [+10 s] extensions adjust
     * the finish without restarting the tick chain; the [onExpire] callback fires at zero.
     */
    private fun startCountdown(
        duration: Duration,
        onTick: (Duration) -> Unit,
        onExpire: () -> Unit,
    ) {
        cancelCountdown()
        val loop = Countdown(handler, uptimeNow() + duration, onTick, onExpire)
        countdown = loop
        loop.start()
    }

    private fun cancelCountdown() {
        countdown?.cancel()
        countdown = null
    }

    private fun uptimeNow(): Duration = SystemClock.uptimeMillis().milliseconds
}

/** The ticking loop behind the mission and emergency countdowns. */
private class Countdown(
    private val handler: Handler,
    private var deadline: Duration,
    private val onTick: (Duration) -> Unit,
    private val onExpire: () -> Unit,
) {
    private val tickRunnable =
        object : Runnable {
            override fun run() {
                val remaining = deadline - SystemClock.uptimeMillis().milliseconds
                if (remaining <= Duration.ZERO) {
                    onExpire()
                } else {
                    onTick(remaining)
                    handler.postDelayed(this, ONE_SECOND.inWholeMilliseconds)
                }
            }
        }

    fun start() {
        handler.postDelayed(tickRunnable, ONE_SECOND.inWholeMilliseconds)
    }

    fun extendBy(extra: Duration) {
        deadline += extra
    }

    fun cancel() {
        handler.removeCallbacks(tickRunnable)
    }

    private companion object {
        val ONE_SECOND: Duration = 1.seconds
    }
}
