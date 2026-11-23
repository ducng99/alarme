package dev.tomng.alarme.core.alarmengine.scheduling

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.model.scheduling.NextOccurrenceCalculator
import dev.tomng.alarme.core.model.scheduling.ScheduleIds
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first

@Singleton
class AlarmSchedulerImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val alarms: ArmTarget,
    private val alarmRepository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduleIdStore: ScheduleIdStore,
    private val warmUpRule: WarmUpRule,
    private val clock: AppClock,
) : AlarmScheduler {

    private val armed = ConcurrentHashMap<Int, PendingIntent>()

    override suspend fun rearm(reason: RescheduleReason): SchedulingResult {
        cancelIfArmed(RequestCodes.RING)
        cancelIfArmed(RequestCodes.WARMUP)
        cancelIfArmed(RequestCodes.PREPARE)

        val now = clock.now()
        val zone = clock.zone()
        val enabledAlarms = alarmRepository.observeAlarms().first().filter { it.enabled }

        val missed = enabledAlarms.filter { alarm ->
            alarm.nextOccurrence?.let { it < now - MISSED_GRACE } ?: false
        }

        val next =
            enabledAlarms
                .mapNotNull { alarm ->
                    NextOccurrenceCalculator.nextOccurrence(alarm, now, zone)?.let { alarm to it }
                }
                .minWithOrNull(compareBy({ (_, at) -> at }, { (alarm, _) -> alarm.id }))

        if (next == null) {
            scheduleIdStore.clear()
            return SchedulingResult(next = null, nextAt = null, missed = missed)
        }

        val (alarm, occurrence) = next
        val deviceId = settingsRepository.deviceId()
        val scheduleId = ScheduleIds.format(deviceId, alarm.id, occurrence, now, zone)
        scheduleIdStore.save(scheduleId.raw)

        val armed =
            alarms.setAlarmClock(
                AlarmClockInfo(occurrence.toEpochMilliseconds(), showAlarmsPendingIntent()),
                pendingIntent(
                    requestCode = RequestCodes.RING,
                    intent =
                        AlarmIntents.ringIntent(
                            context = context,
                            alarmId = alarm.id,
                            scheduleId = scheduleId.raw,
                            alarmTimeMillis = occurrence.toEpochMilliseconds(),
                            triggerType = TriggerType.NORMAL,
                        ),
                ),
            )

        val warmUp = warmUpRule.get()
        if (warmUp != null) {
            alarms.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                occurrence.toEpochMilliseconds() - warmUp.leadMillis,
                pendingIntent(
                    requestCode = RequestCodes.WARMUP,
                    intent =
                        AlarmIntents.warmupIntent(
                            context,
                            acquireWakeLock = warmUp.acquireWakeLock,
                        ),
                ),
            )
        }

        PreparePolicy.prepareAt(
                hour = alarm.hour,
                minute = alarm.minute,
                skipped = alarm.isSkipped,
                triggerType = TriggerType.NORMAL,
                occurrence = occurrence,
                hasWarmUpRule = warmUp != null,
                now = now,
            )
            ?.let { prepareAt ->
                alarms.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    prepareAt.toEpochMilliseconds(),
                    pendingIntent(
                        requestCode = RequestCodes.PREPARE,
                        intent = AlarmIntents.prepareIntent(context),
                    ),
                )
            }

        return SchedulingResult(next = alarm, nextAt = occurrence, missed = missed, armed = armed)
    }

    override fun armWakeUpCheck(alarmId: Long, at: Instant) {
        alarms.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilliseconds(),
            pendingIntent(
                requestCode = RequestCodes.WAKE_UP_CHECK,
                intent =
                    AlarmIntents.wakeUpCheckIntent(
                        context = context,
                        alarmId = alarmId,
                        alarmTimeMillis = at.toEpochMilliseconds(),
                    ),
            ),
        )
    }

    override fun cancelWakeUpCheck() {
        cancelIfArmed(RequestCodes.WAKE_UP_CHECK)
    }

    /**
     * The snooze finish: `setAlarmClock` (status-bar icon + doze-proof) alongside the already armed
     * next normal occurrence — a crash mid-snooze must not lose either.
     */
    override fun armSnooze(alarmId: Long, scheduleId: String?, at: Instant, count: Int) {
        val armedId = scheduleId ?: scheduleIdStore.load() ?: return
        alarms.setAlarmClock(
            AlarmClockInfo(at.toEpochMilliseconds(), showAlarmsPendingIntent()),
            pendingIntent(
                requestCode = RequestCodes.SNOOZE,
                intent =
                    AlarmIntents.ringIntent(
                        context = context,
                        alarmId = alarmId,
                        scheduleId = armedId,
                        alarmTimeMillis = at.toEpochMilliseconds(),
                        triggerType = TriggerType.SNOOZE,
                        snoozeCount = count,
                    ),
            ),
        )
    }

    override fun cancelSnooze() {
        cancelIfArmed(RequestCodes.SNOOZE)
    }

    override fun armRetryRing(delivery: RingDelivery, at: Instant) {
        val scheduleId = delivery.scheduleId ?: scheduleIdStore.load() ?: return
        alarms.setAlarmClock(
            AlarmClockInfo(at.toEpochMilliseconds(), showAlarmsPendingIntent()),
            pendingIntent(
                requestCode = RequestCodes.RING,
                intent =
                    AlarmIntents.ringIntent(
                        context = context,
                        alarmId = delivery.alarmId,
                        scheduleId = scheduleId,
                        alarmTimeMillis = delivery.alarmTimeMillis,
                        triggerType = delivery.triggerType,
                        isRetry = true,
                        snoozeCount = delivery.snoozeCount,
                    ),
            ),
        )
    }

    /** The status-bar alarm icon opens the app when tapped. */
    private fun showAlarmsPendingIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        }

    private fun pendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            .also { armed[requestCode] = it }

    /**
     * Cancels by cached instance first (deterministic), then by the classic `FLAG_NO_CREATE`
     * lookup, with constant-request-code replacement semantics.
     */
    private fun cancelIfArmed(requestCode: Int) {
        armed[requestCode]?.let { cached ->
            alarms.cancel(cached)
            armed.remove(requestCode)
            return
        }
        val base = Intent().setPackage(context.packageName)
        val lookup =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                base,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        if (lookup != null) {
            Log.d("Alarme.Scheduler", "cancel by NO_CREATE lookup for $requestCode")
            alarms.cancel(lookup)
        }
    }

    private companion object {
        /** An alarm due less than a minute ago is just slow delivery, not missed. */
        val MISSED_GRACE = 60.seconds
    }
}
