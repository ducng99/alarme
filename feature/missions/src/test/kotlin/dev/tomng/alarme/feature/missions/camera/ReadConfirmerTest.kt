package dev.tomng.alarme.feature.missions.camera

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReadConfirmerTest {

    private val confirmer = ReadConfirmer(requiredReads = 3)

    @Test
    fun `confirms a value only after consecutive identical reads`() {
        assertThat(confirmer.offer("A")).isNull()
        assertThat(confirmer.offer("A")).isNull()
        assertThat(confirmer.offer("A")).isEqualTo("A")
    }

    @Test
    fun `a different value restarts the count`() {
        confirmer.offer("A")
        confirmer.offer("A")
        assertThat(confirmer.offer("B")).isNull()
        assertThat(confirmer.offer("A")).isNull()
    }

    @Test
    fun `a frame without a barcode does not break the streak`() {
        confirmer.offer("A")
        confirmer.offer(null)
        confirmer.offer("A")
        assertThat(confirmer.offer("A")).isEqualTo("A")
    }
}
