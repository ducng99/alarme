package dev.tomng.alarme.feature.settings.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.component.MedallionShape
import dev.tomng.alarme.core.designsystem.component.ShapeMedallion
import dev.tomng.alarme.core.designsystem.component.rememberFeedback
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.feature.settings.R
import dev.tomng.alarme.feature.settings.permissions.PermissionCenterState
import dev.tomng.alarme.feature.settings.permissions.PermissionItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-run setup: one page per missing critical grant. A page whose grant arrives (the user comes
 * back from system settings) shows a check and moves on by itself.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    permissions: PermissionCenterState,
    welcome: Boolean,
    onAllow: (PermissionItem) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The page list is frozen when setup opens so granted pages stay to show their check.
    val pages = remember { onboardingPages(permissions, welcome) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val feedback = rememberFeedback()
    val page = pages[pager.currentPage]
    val granted = page is OnboardingPage.Grant && permissions.isGranted(page.item)

    LaunchedEffect(granted, pager.currentPage) {
        if (granted) {
            val target = pager.currentPage + 1
            feedback.confirm()
            delay(AUTO_ADVANCE_MILLIS)
            // currentPage flips halfway through the scroll and restarts this effect, so the scroll
            // must outlive it.
            scope.launch { pager.animateScrollToPage(target) }
        }
    }
    val next: () -> Unit = {
        if (pager.currentPage == pages.lastIndex) onFinish()
        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    Surface(
        modifier = modifier.fillMaxSize().testTag("onboarding_screen"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                PageContent(pages[index], permissions)
            }
            PageDots(count = pages.size, current = pager.currentPage)
            Column(
                modifier =
                    Modifier.widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Button(
                    onClick = {
                        when (page) {
                            is OnboardingPage.Grant -> if (granted) next() else onAllow(page.item)
                            else -> next()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(ButtonDefaults.MediumContainerHeight)
                            .testTag("onboarding_primary"),
                ) {
                    Text(
                        text = primaryLabel(page, granted),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (page is OnboardingPage.Ready) {
                    Spacer(Modifier.height(ButtonDefaults.MinHeight))
                } else {
                    TextButton(
                        onClick = if (page is OnboardingPage.Grant) next else onFinish,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_skip"),
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }
        }
    }
}

@Composable
private fun primaryLabel(page: OnboardingPage, granted: Boolean): String =
    when (page) {
        OnboardingPage.Welcome -> stringResource(R.string.onboarding_welcome_start)
        OnboardingPage.Ready -> stringResource(R.string.onboarding_done)
        is OnboardingPage.Grant ->
            stringResource(if (granted) R.string.onboarding_continue else R.string.onboarding_allow)
    }

@Composable
private fun PageContent(page: OnboardingPage, permissions: PermissionCenterState) {
    when (page) {
        OnboardingPage.Welcome ->
            Hero(
                icon = Icons.Outlined.WbSunny,
                shape = MedallionShape.Sunny,
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                done = false,
            )
        OnboardingPage.Ready ->
            Hero(
                icon = Icons.Outlined.AlarmOn,
                shape = MedallionShape.Flower,
                title = stringResource(R.string.onboarding_ready_title),
                body = stringResource(R.string.onboarding_ready_body),
                done = true,
            )
        is OnboardingPage.Grant ->
            Hero(
                icon = page.item.onboardingIcon(),
                shape = MedallionShape.Cookie,
                title = stringResource(page.item.onboardingTitle()),
                body = stringResource(page.item.onboardingBody()),
                done = permissions.isGranted(page.item),
                tag = "onboarding_step_${page.item.name}",
            )
    }
}

@Composable
private fun Hero(
    icon: ImageVector,
    shape: MedallionShape,
    title: String,
    body: String,
    done: Boolean,
    tag: String = "onboarding_hero",
) {
    val container by
        animateColorAsState(
            if (done) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primaryContainer,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "heroColor",
        )
    val grantedLabel = stringResource(R.string.onboarding_granted)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeMedallion(
            shape = shape,
            size = 184.dp,
            color = container,
        ) {
            val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            AnimatedContent(
                targetState = done,
                transitionSpec = { (scaleIn(pop) + fadeIn()) togetherWith fadeOut() },
                label = "heroIcon",
            ) { granted ->
                Icon(
                    imageVector = if (granted) Icons.Filled.Check else icon,
                    contentDescription = if (granted) grantedLabel else null,
                    tint =
                        if (granted) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(Spacing.m))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val description = stringResource(R.string.onboarding_step, current + 1, count)
    Row(
        modifier =
            Modifier.padding(vertical = Spacing.s).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        repeat(count) { index ->
            val width by
                animateDpAsState(
                    if (index == current) 24.dp else 8.dp,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "dot",
                )
            Box(
                modifier =
                    Modifier.size(width = width, height = 8.dp)
                        .background(
                            if (index <= current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.shapes.extraLarge,
                        ),
            )
        }
    }
}

private fun PermissionItem.onboardingIcon(): ImageVector =
    when (this) {
        PermissionItem.NOTIFICATIONS -> Icons.Outlined.Notifications
        PermissionItem.EXACT_ALARM -> Icons.Outlined.Alarm
        PermissionItem.FULL_SCREEN_INTENT -> Icons.Outlined.OpenInFull
        else -> Icons.Outlined.BatterySaver
    }

private fun PermissionItem.onboardingTitle(): Int =
    when (this) {
        PermissionItem.NOTIFICATIONS -> R.string.onboarding_notifications_title
        PermissionItem.EXACT_ALARM -> R.string.onboarding_exact_alarm_title
        PermissionItem.FULL_SCREEN_INTENT -> R.string.onboarding_full_screen_title
        else -> R.string.onboarding_battery_title
    }

private fun PermissionItem.onboardingBody(): Int =
    when (this) {
        PermissionItem.NOTIFICATIONS -> R.string.onboarding_notifications_body
        PermissionItem.EXACT_ALARM -> R.string.onboarding_exact_alarm_body
        PermissionItem.FULL_SCREEN_INTENT -> R.string.onboarding_full_screen_body
        else -> R.string.onboarding_battery_body
    }

private const val AUTO_ADVANCE_MILLIS = 700L
