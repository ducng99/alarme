package dev.tomng.alarme.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Ring-screen time display uses oversized numerals; body text uses the
// platform default so i18n/loc normalization stays predictable.
val AlarmeTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Light,
                fontSize = 96.sp,
                lineHeight = 104.sp,
                letterSpacing = (-1.5).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Light,
                fontSize = 64.sp,
                lineHeight = 70.sp,
                letterSpacing = (-0.5).sp,
            ),
    )
