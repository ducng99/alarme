package dev.tomng.alarme.core.model.alarm

import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable

/**
 * Bitmask of the days of the week an alarm repeats on (ISO order: Monday = bit 1 ... Sunday = bit
 * 64). An empty mask means the alarm is a one-shot: it fires on the next wall-clock occurrence
 * only.
 */
@JvmInline
@Serializable
value class DaysOfWeek(val bits: Int) {

    val isRepeating: Boolean
        get() = bits != 0

    operator fun contains(day: DayOfWeek): Boolean = (bits and day.bit()) != 0

    fun toSet(): Set<DayOfWeek> = DayOfWeek.entries.filter { contains(it) }.toSet()

    companion object {
        val NONE = DaysOfWeek(0)
        val EVERY_DAY = DaysOfWeek(0b1111111)

        fun of(vararg days: DayOfWeek): DaysOfWeek =
            DaysOfWeek(days.fold(0) { acc, day -> acc or day.bit() })

        fun ofSet(days: Set<DayOfWeek>): DaysOfWeek =
            DaysOfWeek(days.fold(0) { acc, day -> acc or day.bit() })

        private fun DayOfWeek.bit(): Int = 1 shl ordinal
    }
}
