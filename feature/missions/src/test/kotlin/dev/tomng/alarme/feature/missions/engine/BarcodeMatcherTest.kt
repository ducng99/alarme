package dev.tomng.alarme.feature.missions.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The barcode rule: the scanned value must equal the registered one, exactly. */
class BarcodeMatcherTest {

    @Test
    fun `an exact value match passes`() {
        assertThat(BarcodeMatcher.matches(registered = "4006381333931", scanned = "4006381333931"))
            .isTrue()
    }

    @Test
    fun `a prefix or containment is not a match`() {
        assertThat(BarcodeMatcher.matches("4006381333931", "400638133393")).isFalse()
        assertThat(BarcodeMatcher.matches("4006381333931", "40063813339312")).isFalse()
        assertThat(BarcodeMatcher.matches("abc", "xabcy")).isFalse()
    }

    @Test
    fun `case matters`() {
        assertThat(BarcodeMatcher.matches("AbC", "abc")).isFalse()
    }

    @Test
    fun `nothing registered can never match`() {
        assertThat(BarcodeMatcher.matches("", "anything")).isFalse()
    }
}
