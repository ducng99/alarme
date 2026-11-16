package dev.tomng.alarme.core.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The clock face text: `7:00 AM` with the meridiem set small, as one text node so the whole label
 * reads correctly to screen readers and to text matching. The text shrinks down to fit narrow
 * layouts instead of clipping.
 */
@Composable
fun ClockTime(
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = LocalContentColor.current,
) {
    BasicText(
        text = clockTimeText(hour, minute, style),
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = 1,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = 32.sp,
                maxFontSize = style.fontSize,
                stepSize = 2.sp,
            ),
    )
}

fun clockText(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute)
        .format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )

private fun clockTimeText(hour: Int, minute: Int, style: TextStyle): AnnotatedString {
    val text = clockText(hour, minute)
    val tail = text.substringAfterLast(' ', missingDelimiterValue = "")
    if (tail.isEmpty() || tail.none { it.isLetter() }) return AnnotatedString(text)

    val head = text.dropLast(tail.length).trimEnd()
    return buildAnnotatedString {
        append(head)
        append(' ')
        withStyle(
            SpanStyle(
                fontSize = (style.fontSize.value * MERIDIEM_RATIO).sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
        ) {
            append(tail)
        }
    }
}

private const val MERIDIEM_RATIO = 0.4f
