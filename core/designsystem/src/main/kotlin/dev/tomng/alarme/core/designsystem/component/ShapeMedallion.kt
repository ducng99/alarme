package dev.tomng.alarme.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** A Material 3 Expressive hero: a tonal shape (cookie, sunny, burst...) behind centred content. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeMedallion(
    modifier: Modifier = Modifier,
    shape: MedallionShape = MedallionShape.Cookie,
    size: Dp = 120.dp,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.size(size).clip(polygonFor(shape).toShape()).background(color),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun polygonFor(shape: MedallionShape): RoundedPolygon =
    when (shape) {
        MedallionShape.Cookie -> MaterialShapes.Cookie9Sided
        MedallionShape.Sunny -> MaterialShapes.Sunny
        MedallionShape.SoftBurst -> MaterialShapes.SoftBurst
        MedallionShape.Clover -> MaterialShapes.Clover4Leaf
        MedallionShape.Flower -> MaterialShapes.Flower
    }
