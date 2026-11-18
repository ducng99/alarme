package dev.tomng.alarme.core.alarmengine.ring

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.scheduling.StartFailurePolicy
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Test

/** Retry a blocked FGS start once, within a 55 s lateness budget. */
class StartFailurePolicyTest {

    private val scheduledAt = Instant.parse("2026-11-04T06:00:00Z")

    @Test
    fun `retries a fresh on-time delivery`() {
        assertThat(
                StartFailurePolicy.shouldRetry(
                    now = scheduledAt + 5.seconds,
                    scheduledAt = scheduledAt,
                    isRetry = false,
                ),
            )
            .isTrue()
    }

    @Test
    fun `retries only once`() {
        assertThat(
                StartFailurePolicy.shouldRetry(
                    now = scheduledAt + 5.seconds,
                    scheduledAt = scheduledAt,
                    isRetry = true,
                ),
            )
            .isFalse()
    }

    @Test
    fun `never retries deliveries older than 55 seconds`() {
        assertThat(
                StartFailurePolicy.shouldRetry(
                    now = scheduledAt + 56.seconds,
                    scheduledAt = scheduledAt,
                    isRetry = false,
                ),
            )
            .isFalse()
    }
}
