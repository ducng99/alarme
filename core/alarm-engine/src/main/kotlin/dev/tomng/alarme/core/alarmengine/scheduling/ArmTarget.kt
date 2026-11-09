package dev.tomng.alarme.core.alarmengine.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.util.Log

/**
 * The device's alarm-arming surface. Arming can fail even with the alarm saved: without the
 * exact-alarm grant every call throws [SecurityException]. Degrading to "armed nothing" keeps the
 * edit saveable and leaves fixing the grant to the permission center.
 */
interface ArmTarget {
    /** @return whether the alarm was armed. */
    fun setAlarmClock(info: AlarmManager.AlarmClockInfo, operation: PendingIntent): Boolean

    /** @return whether the alarm was armed. */
    fun setExactAndAllowWhileIdle(type: Int, at: Long, operation: PendingIntent): Boolean

    fun cancel(operation: PendingIntent)
}

class AndroidArmTarget(
    private val armClock: (AlarmManager.AlarmClockInfo, PendingIntent) -> Boolean,
    private val armExact: (Int, Long, PendingIntent) -> Boolean,
    private val cancelArm: (PendingIntent) -> Unit,
) : ArmTarget {

    constructor(
        alarmManager: AlarmManager,
    ) : this(
        armClock = { info, operation ->
            alarmManager.setAlarmClock(info, operation)
            true
        },
        armExact = { type, at, operation ->
            alarmManager.setExactAndAllowWhileIdle(type, at, operation)
            true
        },
        cancelArm = { operation -> alarmManager.cancel(operation) },
    )

    override fun setAlarmClock(
        info: AlarmManager.AlarmClockInfo,
        operation: PendingIntent,
    ): Boolean = guard("setAlarmClock") { armClock(info, operation) }

    override fun setExactAndAllowWhileIdle(
        type: Int,
        at: Long,
        operation: PendingIntent,
    ): Boolean = guard("setExactAndAllowWhileIdle") { armExact(type, at, operation) }

    override fun cancel(operation: PendingIntent) {
        guard("cancel") {
            cancelArm(operation)
            true
        }
    }

    private inline fun guard(call: String, block: () -> Boolean): Boolean =
        try {
            block()
        } catch (e: SecurityException) {
            Log.w(TAG, "$call refused: the exact-alarm grant is missing", e)
            false
        }

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
