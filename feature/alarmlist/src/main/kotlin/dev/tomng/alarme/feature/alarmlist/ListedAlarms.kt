package dev.tomng.alarme.feature.alarmlist

import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.scheduling.NextOccurrenceCalculator
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The stored alarms in list order, each carrying its next ring. Emits again whenever the soonest
 * ring passes, so an alarm that just rang moves on to its next day without a database change.
 */
internal fun listedUntilRingsPass(stored: List<Alarm>, clock: AppClock): Flow<List<Alarm>> = flow {
    while (true) {
        val now = clock.now()
        val listed =
            stored
                .map { alarm ->
                    alarm.copy(
                        nextOccurrence =
                            NextOccurrenceCalculator.nextOccurrence(alarm, now, clock.zone()),
                    )
                }
                .sortedWith(LIST_ORDER)
        emit(listed)
        val soonest = listed.mapNotNull { it.nextOccurrence }.minOrNull() ?: return@flow
        delay(soonest - clock.now())
    }
}

private val LIST_ORDER: Comparator<Alarm> =
    compareByDescending<Alarm> { it.enabled }
        .thenBy { it.nextOccurrence ?: Instant.DISTANT_FUTURE }
        .thenBy { it.hour * 60 + it.minute }
        .thenBy { it.id }
