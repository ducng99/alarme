package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.scheduling.ScheduleId
import kotlin.time.Instant
import kotlinx.coroutines.launch
import org.junit.Test

/**
 * The session store wraps the pure reducer in a process-wide holder: the service, the activity and
 * the effect executor all talk to the same live session through it.
 */
class RingSessionStoreTest {

    private val now: Instant = Instant.parse("2026-11-10T06:00:00Z")
    private val store = RingSessionStore()

    private val scheduleId =
        ScheduleId(
            deviceId = "d",
            alarmId = 7L,
            alarmTime = "20261110_0600",
            nowTime = "20261109_2200",
        )

    private fun request() =
        RingEvent.AlarmRingRequest(
            alarm = ringAlarm(),
            scheduleId = scheduleId,
            trigger = RingTrigger.SCHEDULED,
            ringEventId = 11L,
            startedAt = now,
        )

    private fun ringAlarm() =
        dev.tomng.alarme.core.model.alarm.Alarm(
            id = 7L,
            name = "Work",
            hour = 6,
            minute = 0,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun `the store starts idle`() {
        assertThat(store.state.value).isEqualTo(RingState.Idle)
    }

    @Test
    fun `submitting a ring request exposes the ringing state and the effects in order`() {
        val effects = mutableListOf<RingEffect>()
        store.addEffectListener(effects::add)

        store.submit(request())

        assertThat(store.state.value).isInstanceOf(RingState.Ringing::class.java)
        assertThat(effects).containsExactly(RingEffect.Ring(ringAlarm()))
    }

    @Test
    fun `a duplicate ring request is ignored`() {
        val effects = mutableListOf<RingEffect>()
        store.addEffectListener(effects::add)

        store.submit(request())
        store.submit(request())

        assertThat(effects).hasSize(1)
    }

    @Test
    fun `removed listeners receive nothing further`() {
        val effects = mutableListOf<RingEffect>()
        store.addEffectListener(effects::add)

        store.submit(request())
        store.removeEffectListener(effects::add)
        store.submit(RingEvent.OnDismissClick(now))

        assertThat(effects).hasSize(1)
    }

    @Test
    fun `state changes are visible to collectors`() {
        val seen = mutableListOf<RingState>()

        kotlinx.coroutines.runBlocking {
            val job =
                launch(kotlinx.coroutines.Dispatchers.Unconfined) {
                    store.state.collect { seen.add(it) }
                }
            store.submit(request())
            store.submit(RingEvent.OnDismissClick(now))
            job.cancel()
        }

        assertThat(seen[1]).isInstanceOf(RingState.Ringing::class.java)
        assertThat(seen.last()).isEqualTo(RingState.Idle)
        assertThat(seen).hasSize(3)
    }
}
