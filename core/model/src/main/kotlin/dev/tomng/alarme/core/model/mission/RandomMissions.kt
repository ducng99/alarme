package dev.tomng.alarme.core.model.mission

import kotlin.random.Random

/** Picks which missions of a chain run for one ring when the alarm uses random missions. */
object RandomMissions {

    /**
     * Indices into a chain of [size] missions. A null [count] keeps the whole chain in its order;
     * otherwise [count] distinct missions (clamped to 1..[size]) come back in random order.
     */
    fun pickIndices(size: Int, count: Int?, random: Random): List<Int> {
        val indices = List(size) { it }
        if (count == null || size == 0) return indices
        return indices.shuffled(random).take(count.coerceIn(1, size))
    }
}
