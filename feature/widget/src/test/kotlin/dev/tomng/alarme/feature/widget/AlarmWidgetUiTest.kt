package dev.tomng.alarme.feature.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widget's composition: what a placed widget actually renders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmWidgetUiTest {

    @Test
    fun `an armed alarm shows its time and label`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            WidgetUi(
                content =
                    WidgetContent(
                        hasAlarm = true,
                        timeText = "7:30 AM",
                        label = "Work",
                        countdownBaseMillis = null,
                    ),
                launch = null,
            )
        }

        onNode(hasText("7:30 AM")).assertExists()
        onNode(hasText("Work")).assertExists()
    }

    @Test
    fun `no alarm shows the empty state`() = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext())
        provideComposable {
            WidgetUi(
                content =
                    WidgetContent(
                        hasAlarm = false,
                        timeText = "",
                        label = "",
                        countdownBaseMillis = null,
                    ),
                launch = null,
            )
        }

        onNode(hasText("No alarm set")).assertExists()
        onNode(hasText("Tap to add one")).assertExists()
    }
}
