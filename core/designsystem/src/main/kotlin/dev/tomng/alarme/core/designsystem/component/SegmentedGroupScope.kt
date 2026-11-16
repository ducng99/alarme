package dev.tomng.alarme.core.designsystem.component

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable

/** Collects the rows of a [SegmentedGroup]; each row receives its position-aware shapes. */
class SegmentedGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (shapes: ListItemShapes) -> Unit) {
        rows += content
    }
}
