package dev.tomng.alarme.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/**
 * A Material 3 Expressive hero: a tonal shape (cookie, sunny, burst...) behind centred content.
 * When [rotating] the shape turns slowly while the content stays upright; with [pulsing] it also
 * breathes, which suits a ringing alarm.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeMedallion(
    modifier: Modifier = Modifier,
    shape: MedallionShape = MedallionShape.Cookie,
    size: Dp = 120.dp,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    rotating: Boolean = false,
    pulsing: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val polygon = polygonFor(shape)
    val transition = rememberInfiniteTransition(label = "medallion")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = if (rotating) FULL_TURN else 0f,
            animationSpec = infiniteRepeatable(tween(ROTATION_MILLIS, easing = LinearEasing)),
            label = "rotation",
        )
    val scale by
        transition.animateFloat(
            initialValue = 1f,
            targetValue = if (pulsing) PULSE_SCALE else 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
            label = "pulse",
        )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier.size(size)
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(polygon.toShape())
                    .background(color),
        )
        content()
    }
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

private const val FULL_TURN = 360f
private const val ROTATION_MILLIS = 24_000
private const val PULSE_MILLIS = 900
private const val PULSE_SCALE = 1.08f
