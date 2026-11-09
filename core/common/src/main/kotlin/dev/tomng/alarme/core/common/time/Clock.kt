package dev.tomng.alarme.core.common.time

import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Injectable time source so domain logic and ViewModels stay deterministic under test. */
interface AppClock {
    fun now(): Instant

    fun zone(): TimeZone
}

class SystemAppClock(
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : AppClock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

    override fun zone(): TimeZone = zone
}

/** Mutable clock for tests: advance time by setting [now] between calls. */
class FakeAppClock(
    var now: Instant,
    var zone: TimeZone = TimeZone.UTC,
) : AppClock {
    override fun now(): Instant = now

    override fun zone(): TimeZone = zone

    fun advanceBy(duration: kotlin.time.Duration) {
        now = now + duration
    }
}
