package dev.tomng.alarme.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import javax.inject.Inject
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY: seeds one alarm (wall-clock [hour]:[minute], next occurrence) and re-arms the
 * scheduler, so the delivery chain can be smoke-tested from adb before the editor UI exists:
 * ```
 * adb shell am broadcast -a dev.tomng.alarme.debug.action.SEED_ALARM \
 *   --ei hour 14 --ei minute 32 [--es mission math|shake|squat|step] \
 *   -n dev.tomng.alarme.debug/dev.tomng.alarme.debug.DebugSeedReceiver
 * ```
 */
@AndroidEntryPoint
class DebugSeedReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository

    @Inject lateinit var scheduler: AlarmScheduler

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        if (hour !in 0..23 || minute !in 0..59) {
            Log.w(TAG, "seed ignored: hour=$hour minute=$minute")
            return
        }
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val mission = missionFor(intent.getStringExtra(EXTRA_MISSION))
        val wucMinutes = intent.getIntExtra(EXTRA_WUC_MINUTES, 0)
        val pending = goAsync()
        scope.launch {
            try {
                val id =
                    alarmRepository.upsertAlarm(
                        Alarm(
                            id = Alarm.NEW_ID,
                            name = "smoke test",
                            hour = hour,
                            minute = minute,
                            enabled = true,
                            missions = listOfNotNull(mission),
                            wakeUpCheck = dev.tomng.alarme.core.model.alarm.WakeUpCheck(wucMinutes),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                val result = scheduler.rearm(RescheduleReason.ADD_ALARM)
                Log.i(
                    TAG,
                    "seeded alarm id=$id hour=$hour minute=$minute mission=$mission wuc=${wucMinutes}m " +
                        "nextAt=${result.nextAt}",
                )
            } finally {
                pending.finish()
            }
        }
    }

    /** The mission named by [name], with a short limit so smoke runs stay quick. */
    private fun missionFor(name: String?): Mission? =
        when (name?.lowercase()) {
            "math" -> Mission.Math(timeLimitSeconds = 120, rounds = 1, difficulty = 0)
            "memory" -> Mission.Memory(timeLimitSeconds = 120, rounds = 1, difficulty = 3)
            "shake" -> Mission.Shake(timeLimitSeconds = 120, count = 2)
            "squat" -> Mission.Squat(timeLimitSeconds = 120, count = 1)
            "step" -> Mission.Step(timeLimitSeconds = 120, count = 2)
            "typing" ->
                Mission.Typing(
                    timeLimitSeconds = 120,
                    rounds = 1,
                    typingSentences =
                        listOf(
                            TypingSentence(TypingSentenceType.SHORT, "wake up now"),
                        ),
                )

            "barcode" -> Mission.Barcode(timeLimitSeconds = 120, barcodeValue = "12345")
            "photo" -> Mission.Photo(timeLimitSeconds = 120, photoPath = "")
            "image" ->
                Mission.ImageRecognition(
                    timeLimitSeconds = 120,
                    objects = listOf(ImageRecognitionObject.CHAIR),
                )

            "wordbeat" -> Mission.WordBeat(timeLimitSeconds = 120, rounds = 1)
            else -> null
        }

    companion object {
        const val ACTION = "dev.tomng.alarme.debug.action.SEED_ALARM"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_MISSION = "mission"
        const val EXTRA_WUC_MINUTES = "wuc"
        private const val TAG = "Alarme.DebugSeed"
    }
}
