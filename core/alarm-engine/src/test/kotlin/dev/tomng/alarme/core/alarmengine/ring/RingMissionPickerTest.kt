package dev.tomng.alarme.core.alarmengine.ring

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.InMemoryRandomMissionStore
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.random.Random
import kotlin.time.Instant
import org.junit.Test

class RingMissionPickerTest {

    private val store = InMemoryRandomMissionStore()
    private val missions =
        listOf(
            Mission.Math(rounds = 1),
            Mission.Math(rounds = 2),
            Mission.Math(rounds = 3),
            Mission.Math(rounds = 4),
            Mission.Math(rounds = 5),
        )
    private val alarm =
        Alarm(
            id = 7L,
            name = "Work",
            hour = 6,
            minute = 0,
            missions = missions,
            randomMissionCount = 3,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
        )

    private fun picker(seed: Int) = RingMissionPicker(store, Random(seed))

    @Test
    fun `an alarm without random missions rings its whole chain in order`() {
        val plain = alarm.copy(randomMissionCount = null)

        assertThat(picker(1).pick(plain, scheduleId = "s1").missions).isEqualTo(missions)
    }

    @Test
    fun `a random alarm rings the chosen number of distinct missions`() {
        val picked = picker(1).pick(alarm, scheduleId = "s1").missions

        assertThat(picked).hasSize(3)
        assertThat(picked.toSet()).hasSize(3)
        assertThat(missions).containsAtLeastElementsIn(picked)
    }

    @Test
    fun `the same ring keeps its pick across a snooze or a redelivery`() {
        val first = picker(1).pick(alarm, scheduleId = "s1").missions

        val again = picker(99).pick(alarm, scheduleId = "s1").missions

        assertThat(again).isEqualTo(first)
    }

    @Test
    fun `a new occurrence picks again`() {
        val picker = picker(1)

        val picks = (0 until 20).map { picker.pick(alarm, scheduleId = "s$it").missions }

        assertThat(picks.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `a ring without a schedule id always picks fresh`() {
        picker(1).pick(alarm, scheduleId = null)

        assertThat(store.load()).isNull()
    }

    @Test
    fun `a skip or enable flag change keeps the pick`() {
        val first = picker(1).pick(alarm, scheduleId = "s1").missions
        val touched =
            alarm.copy(isSkipped = true, updatedAt = Instant.parse("2026-11-05T00:00:00Z"))

        assertThat(picker(99).pick(touched, scheduleId = "s1").missions).isEqualTo(first)
    }

    @Test
    fun `an alarm edited since the stored pick picks again`() {
        picker(1).pick(alarm, scheduleId = "s1")
        val edited = alarm.copy(missions = missions.take(2), randomMissionCount = 2)

        val picked = picker(2).pick(edited, scheduleId = "s1").missions

        assertThat(picked).containsExactlyElementsIn(missions.take(2))
    }
}
