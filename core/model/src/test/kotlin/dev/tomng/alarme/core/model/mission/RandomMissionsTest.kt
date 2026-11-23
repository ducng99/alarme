package dev.tomng.alarme.core.model.mission

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class RandomMissionsTest {

    @Test
    fun `no count keeps every mission in order`() {
        assertThat(RandomMissions.pickIndices(size = 4, count = null, random = Random(1)))
            .containsExactly(0, 1, 2, 3)
            .inOrder()
    }

    @Test
    fun `a count picks that many distinct missions`() {
        repeat(50) { seed ->
            val picked = RandomMissions.pickIndices(size = 5, count = 3, random = Random(seed))

            assertThat(picked).hasSize(3)
            assertThat(picked.toSet()).hasSize(3)
            assertThat(picked.all { it in 0 until 5 }).isTrue()
        }
    }

    @Test
    fun `a count equal to the chain only shuffles the order`() {
        val orders =
            (0 until 50).map { seed ->
                RandomMissions.pickIndices(size = 4, count = 4, random = Random(seed))
            }

        orders.forEach { assertThat(it).containsExactly(0, 1, 2, 3) }
        assertThat(orders.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `every mission can be picked`() {
        val seen =
            (0 until 200)
                .flatMap { seed ->
                    RandomMissions.pickIndices(size = 5, count = 1, random = Random(seed))
                }
                .toSet()

        assertThat(seen).containsExactly(0, 1, 2, 3, 4)
    }

    @Test
    fun `an out of range count is clamped to the chain`() {
        assertThat(RandomMissions.pickIndices(size = 3, count = 9, random = Random(1))).hasSize(3)
        assertThat(RandomMissions.pickIndices(size = 3, count = 0, random = Random(1))).hasSize(1)
    }

    @Test
    fun `an empty chain picks nothing`() {
        assertThat(RandomMissions.pickIndices(size = 0, count = 2, random = Random(1))).isEmpty()
    }
}
