package dev.tomng.alarme.feature.missions.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.designsystem.component.MedallionShape
import dev.tomng.alarme.core.designsystem.component.ShapeMedallion

/** The shared chrome for a mission stage: a themed full-screen surface with a tonal header. */
@Composable
internal fun MissionStageScaffold(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tagged = if (testTag != null) Modifier.testTag(testTag) else Modifier
    Surface(
        modifier = modifier.fillMaxSize().then(tagged),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MissionHeader(icon = icon, title = title, subtitle = subtitle)
            content()
        }
    }
}

@Composable
internal fun MissionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShapeMedallion(
            shape = MedallionShape.Cookie,
            size = 48.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The one full-width action a mission offers at the bottom of the screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MissionPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.fillMaxWidth().height(64.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A card that states why a mission cannot run and points at the emergency way out. */
@Composable
internal fun MissionUnavailableCard(text: String, modifier: Modifier = Modifier) {
    GroupCard(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        )
    }
}

/** The big progress readout shared by the sensor missions. */
@Composable
internal fun MissionProgressCard(
    progress: Float,
    valueText: String,
    caption: String,
    valueTag: String,
    progressTag: String,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "missionProgress")
    GroupCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(
                targetState = valueText,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                        (slideOutVertically { height -> -height } + fadeOut())
                },
                label = "missionProgressValue",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(valueTag),
                )
            }
            LinearWavyProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().testTag(progressTag),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A short horizontal shake, replayed whenever [trigger] changes. Missions with a typed answer use
 * it to make a wrong attempt visible without a new string or a blocking dialog.
 */
@Composable
internal fun rememberShakeOffset(trigger: Int, amplitude: Dp = 10.dp): Dp {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec =
                keyframes {
                    durationMillis = SHAKE_MILLIS
                    amplitude.value at 0
                    -amplitude.value at SHAKE_MILLIS / 4
                    amplitude.value at SHAKE_MILLIS / 2
                    -amplitude.value at SHAKE_MILLIS * 3 / 4
                    0f at SHAKE_MILLIS
                },
        )
    }
    return offset.value.dp
}

private const val SHAKE_MILLIS = 360

/**
 * The camera missions keep the preview full-bleed: a scrim dims the top and bottom edges, and the
 * instruction plus main action sit on opaque cards so they stay legible over any scene.
 */
@Composable
internal fun MissionCameraChrome(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    messageIsError: Boolean = false,
    action: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                MissionHeader(
                    icon = icon,
                    title = title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Start,
                        color =
                            if (messageIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    )
                    action()
                }
            }
        }
    }
}
