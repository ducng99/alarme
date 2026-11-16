package dev.tomng.alarme.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

/**
 * A bottom sheet that picks one number on a [NumberWheel], with [unit] beside it. Replaces long
 * plus/minus stepping: any value is one flick away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ValueSheet(
    title: String,
    values: List<Int>,
    initial: Int,
    unit: (Int) -> String,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "value_sheet",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(values.minBy { kotlin.math.abs(it - initial) }) }
    val feedback = rememberFeedback()
    val close: (() -> Unit) -> Unit = { after ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { after() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.xl)
                    .testTag(tag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.padding(vertical = Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                NumberWheel(
                    values = values,
                    selected = value,
                    onSelect = { value = it },
                    label = { it.toString() },
                    contentDescription = title,
                    looping = false,
                    width = 120.dp,
                    testTag = "$tag:wheel",
                )
                Text(
                    text = unit(value),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            ) {
                TextButton(onClick = { close(onDismiss) }) { Text(cancelLabel) }
                Button(
                    onClick = {
                        feedback.confirm()
                        close { onConfirm(value) }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("$tag:ok"),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}
