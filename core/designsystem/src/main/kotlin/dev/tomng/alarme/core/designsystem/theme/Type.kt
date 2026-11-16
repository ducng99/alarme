package dev.tomng.alarme.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

private const val TABULAR = "tnum"

private val Baseline = Typography()

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

/**
 * The Material 3 baseline type scale, with tabular figures on the display styles so clock digits do
 * not shift.
 */
val AlarmeTypography =
    Baseline.copy(
        displayLarge = Baseline.displayLarge.tabular(),
        displayMedium = Baseline.displayMedium.tabular(),
        displaySmall = Baseline.displaySmall.tabular(),
    )
