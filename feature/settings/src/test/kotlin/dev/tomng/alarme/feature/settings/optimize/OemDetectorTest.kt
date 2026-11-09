package dev.tomng.alarme.feature.settings.optimize

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemDetectorTest {

    @Test
    fun `xiaomi and redmi map to the xiaomi guide`() {
        assertThat(OemDetector.detect("Xiaomi", "Redmi")).isEqualTo(OemGuide.XIAOMI)
        assertThat(OemDetector.detect("Xiaomi", "POCO")).isEqualTo(OemGuide.XIAOMI)
    }

    @Test
    fun `huawei and honor are told apart`() {
        assertThat(OemDetector.detect("HUAWEI", "HUAWEI")).isEqualTo(OemGuide.HUAWEI)
        assertThat(OemDetector.detect("HONOR", "HONOR")).isEqualTo(OemGuide.HONOR)
    }

    @Test
    fun `oppo realme and oneplus map to their guides`() {
        assertThat(OemDetector.detect("OPPO", "OPPO")).isEqualTo(OemGuide.OPPO)
        assertThat(OemDetector.detect("realme", "realme")).isEqualTo(OemGuide.OPPO)
        assertThat(OemDetector.detect("OnePlus", "OnePlus")).isEqualTo(OemGuide.ONEPLUS)
    }

    @Test
    fun `vivo and iqoo map to the vivo guide`() {
        assertThat(OemDetector.detect("vivo", "vivo")).isEqualTo(OemGuide.VIVO)
        assertThat(OemDetector.detect("vivo", "iQOO")).isEqualTo(OemGuide.VIVO)
    }

    @Test
    fun `the brand is consulted when the manufacturer is generic`() {
        assertThat(OemDetector.detect("unknown", "Redmi")).isEqualTo(OemGuide.XIAOMI)
    }

    @Test
    fun `everything else gets the generic guide`() {
        assertThat(OemDetector.detect("Google", "Pixel")).isEqualTo(OemGuide.GENERIC)
        assertThat(OemDetector.detect("samsung", "samsung")).isEqualTo(OemGuide.GENERIC)
    }

    @Test
    fun `detection is case and whitespace insensitive`() {
        assertThat(OemDetector.detect("  XIAOMI ", " redmi ")).isEqualTo(OemGuide.XIAOMI)
    }
}
