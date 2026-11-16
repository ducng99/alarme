package dev.tomng.alarme.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.theme.Spacing

/** An inline, in-flow message with an optional single action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineBanner(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: String? = null,
    tone: BannerTone = BannerTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (tone) {
            BannerTone.Neutral -> colors.surfaceContainerHigh to colors.onSurface
            BannerTone.Success -> colors.secondaryContainer to colors.onSecondaryContainer
            BannerTone.Warning -> colors.tertiaryContainer to colors.onTertiaryContainer
            BannerTone.Error -> colors.errorContainer to colors.onErrorContainer
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null)
                Column(modifier = Modifier.padding(start = Spacing.l).weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (body != null) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = SUPPORTING_ALPHA),
                        )
                    }
                }
            }
            if (actionLabel != null && onAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onAction,
                        shapes = ButtonDefaults.shapes(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = content,
                                contentColor = container,
                            ),
                        modifier = actionModifier,
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/** A centred hero for screens with nothing to list yet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeMedallion(shape = MedallionShape.Sunny, size = 144.dp) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xl))
            FilledTonalButton(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
                modifier = actionModifier.height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                if (actionIcon != null) {
                    Icon(
                        actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.MediumIconSize),
                    )
                    Spacer(
                        Modifier.size(
                            ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight),
                        ),
                    )
                }
                Text(actionLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A small coloured dot for a pass/fail status, with its meaning carried in text elsewhere. */
@Composable
fun StatusDot(ok: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(10.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        content = {},
    )
}

private const val SUPPORTING_ALPHA = 0.8f
