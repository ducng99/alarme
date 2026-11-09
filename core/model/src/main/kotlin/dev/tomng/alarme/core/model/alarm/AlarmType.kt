package dev.tomng.alarme.core.model.alarm

import kotlinx.serialization.Serializable

/** Kind of alarm. `QUICK` alarms are one-shot ("in X h Y m") and not editable afterwards. */
@Serializable
enum class AlarmType {
    NORMAL,
    QUICK,
}
