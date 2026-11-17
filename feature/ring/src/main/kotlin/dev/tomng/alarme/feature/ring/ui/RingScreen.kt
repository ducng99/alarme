package dev.tomng.alarme.feature.ring.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.designsystem.component.ClockTime
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.feature.ring.R
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.delay

/**
 * The ringing screen: one plain layout per stage of the live session. Nothing here touches the
 * store directly - the activity routes every intent into the session, and the session drives the
 * UI.
 *
 * [nowProvider] feeds the clock and the countdowns; the authoritative timeouts live in the
 * executor, these are only the display.
 */
@Composable
fun RingScreen(
    state: RingState,
    callbacks: RingScreenCallbacks,
    modifier: Modifier = Modifier,
    nowProvider: () -> Instant = { Instant.fromEpochMilliseconds(System.currentTimeMillis()) },
) {
    if (state is RingState.Idle || state is RingState.WakeUpCheckPending) return
    val shown = state
    Box(modifier = modifier.fillMaxSize()) {
        when (shown) {
            is RingState.Ringing ->
                when (val stage = shown.stage) {
                    is RingStage.Ring ->
                        RingFrame { LoudAlarmContent(shown, callbacks, nowProvider) }
                    is RingStage.PrepareMission ->
                        RingFrame { PrepareContent(stage, shown.alarm.missions.size, callbacks) }
                    is RingStage.Mission ->
                        MissionStageContent(stage, shown.alarm.missions.size, callbacks)
                    is RingStage.Snoozing ->
                        RingFrame {
                            SnoozedContent(stage, shown.alarm.missions.isNotEmpty(), callbacks)
                        }
                    is RingStage.Emergency ->
                        RingFrame { EmergencyContent(stage.remaining, callbacks) }
                }
            is RingState.WakeUpCheck ->
                RingFrame { WakeUpCheckContent(shown, callbacks, nowProvider) }
            RingState.Idle,
            is RingState.WakeUpCheckPending -> Unit
        }
    }
}

@Composable
internal fun RingFrame(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .testTag("ring:content"),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.widthIn(max = MAX_WIDTH).fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun LoudAlarmContent(
    state: RingState.Ringing,
    callbacks: RingScreenCallbacks,
    nowProvider: () -> Instant,
) {
    val alarm = state.alarm
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        CurrentTime(nowProvider)
        if (alarm.name.isNotBlank()) {
            Text(
                text = alarm.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        val memo = alarm.memoOption.memo
        if (memo.isNotBlank()) {
            Text(
                text = memo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
        Spacer(Modifier.weight(1f))
        if (alarm.snoozeOption.enabled && !state.snooze.exhausted) {
            SnoozeButton(
                minutes = alarm.snoozeOption.minutes,
                left =
                    if (state.snooze.limit == SnoozeOption.UNLIMITED) null
                    else state.snooze.limit - state.snooze.count,
                onClick = callbacks::onSnooze,
            )
            Spacer(Modifier.height(Spacing.m))
        }
        SlideToDismiss(
            onDismiss = callbacks::onDismiss,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
    }
}

@Composable
private fun CurrentTime(nowProvider: () -> Instant) {
    var now by remember { mutableStateOf(nowProvider()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowProvider()
            delay(MINUTE_MS - now.toEpochMilliseconds() % MINUTE_MS)
        }
    }
    val time =
        java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(ZoneId.systemDefault())
    ClockTime(
        hour = time.hour,
        minute = time.minute,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = CLOCK_SIZE),
    )
}

@Composable
private fun SnoozeButton(minutes: Int, left: Int?, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = SNOOZE_HEIGHT).testTag("ring:snooze"),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.ring_snooze_minutes, minutes),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            if (left != null) {
                Text(
                    text = stringResource(R.string.ring_snooze_left, left),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

private val MAX_WIDTH = 560.dp
private val SNOOZE_HEIGHT = 72.dp
private val CLOCK_SIZE = 80.sp
private const val MINUTE_MS = 60_000L
