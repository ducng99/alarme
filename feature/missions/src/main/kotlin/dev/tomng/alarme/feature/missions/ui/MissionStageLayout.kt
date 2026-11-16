package dev.tomng.alarme.feature.missions.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.theme.Spacing

/** A tonal container for a mission's puzzle, prompt or readout. */
@Composable
internal fun MissionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(content = content)
    }
}

/** The shared chrome for a mission stage: a themed full-screen surface with a tonal header. */
@Composable
internal fun MissionStageScaffold(
    title: String,
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
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                modifier =
                    Modifier.widthIn(max = MAX_STAGE_WIDTH)
                        .fillMaxSize()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                MissionHeader(title = title, subtitle = subtitle)
                content()
            }
        }
    }
}

@Composable
internal fun MissionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
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
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}

/** A card that states why a mission cannot run and points at the emergency way out. */
@Composable
internal fun MissionUnavailableCard(text: String, modifier: Modifier = Modifier) {
    MissionCard(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.l),
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
    MissionCard(modifier = modifier) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(valueTag),
            )
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

private val MAX_STAGE_WIDTH = 560.dp
private const val SCRIM_ALPHA = 0.5f

/**
 * The camera missions keep the preview full-bleed: a scrim dims the top and bottom edges, and the
 * instruction plus main action sit on opaque cards so they stay legible over any scene.
 */
@Composable
internal fun MissionCameraChrome(
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
                                MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA),
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA),
                            ),
                        ),
                    ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.xl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                MissionHeader(
                    title = title,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
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
