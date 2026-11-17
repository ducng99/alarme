package dev.tomng.alarme.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.theme.Spacing

/**
 * The frame shared by secondary screens: a large flexible app bar that collapses as the content
 * scrolls, the back affordance, and an optional snackbar host.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlarmeScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    backTag: String? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    collapsible: Boolean = true,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val collapsingBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollBehavior = if (collapsible) collapsingBehavior else null
    val density = LocalDensity.current
    var width by remember { mutableStateOf(0.dp) }
    val gutter = ((width - Spacing.maxContentWidth) / 2).coerceAtLeast(0.dp)
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { width = with(density) { it.width.toDp() } }
                .then(
                    if (scrollBehavior != null) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    },
                ),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle =
                    if (subtitle != null) {
                        { Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = if (backTag != null) Modifier.testTag(backTag) else Modifier,
                        ) {
                            Icon(
                                imageVector = backIcon,
                                contentDescription = backContentDescription,
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                windowInsets =
                    TopAppBarDefaults.windowInsets.add(WindowInsets(left = gutter, right = gutter)),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize()) {
                content(innerPadding)
            }
        }
    }
}

/**
 * Merges the scaffold's insets with the screen's own padding around a scrolling list. The scaffold
 * padding is read lazily because it changes as the app bar grows or collapses.
 */
@Composable
fun rememberListPadding(
    innerPadding: PaddingValues,
    horizontal: Dp,
    bottom: Dp,
): PaddingValues =
    remember(innerPadding, horizontal, bottom) { ListPadding(innerPadding, horizontal, bottom) }

@Stable
private class ListPadding(
    private val inner: PaddingValues,
    private val horizontal: Dp,
    private val bottom: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        inner.calculateLeftPadding(layoutDirection) + horizontal

    override fun calculateTopPadding(): Dp = inner.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        inner.calculateRightPadding(layoutDirection) + horizontal

    override fun calculateBottomPadding(): Dp = inner.calculateBottomPadding() + bottom
}
