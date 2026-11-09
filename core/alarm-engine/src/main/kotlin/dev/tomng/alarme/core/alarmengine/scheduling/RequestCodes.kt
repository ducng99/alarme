package dev.tomng.alarme.core.alarmengine.scheduling

/** PendingIntent request codes — constants so a new arming always replaces the previous one. */
object RequestCodes {
    /** The ring itself (`setAlarmClock`). */
    const val RING = 1

    /** OEM pre-wake a few seconds before the ring (`setExactAndAllowWhileIdle`). */
    const val WARMUP = 2

    /** Early-wake prepare five/fifteen minutes before the ring. */
    const val PREPARE = 3

    /** Wake-up check re-ring after dismiss. */
    const val WAKE_UP_CHECK = 100

    /** The live snooze finish. */
    const val SNOOZE = 4
}

/** How the armed delivery was meant to be triggered. */
enum class TriggerType {
    NORMAL,
    SNOOZE,
    WAKE_UP_CHECK,
}

/** Every reason the app re-arms the single system alarm. */
enum class RescheduleReason {
    ADD_ALARM,
    MODIFY_ALARM,
    DELETE_ALARM,
    DUPLICATE_ALARM,
    TURN_ON_ALARM,
    TURN_OFF_ALARM,
    RECOVER_DISABLED_ALARM,
    RING_ALARM,
    PROCESS_SKIP,
    SNOOZE_ALARM,
    DISMISS,
    WAKE_UP_CHECK_START,
    WAKE_UP_CHECK_FINISH,
    REBOOT,
    REBOOT_MISSED_ALARM,
    APP_UPDATE,
    TIME_CHANGED,
    EXACT_ALARM_PERMISSION_CHANGED,
    MISSED_ALARM,
    INVALID_ALARM,
    FALSE_ALARM,
    EXPIRED_ALARM,
    BACKUP_RESTORE,
}
