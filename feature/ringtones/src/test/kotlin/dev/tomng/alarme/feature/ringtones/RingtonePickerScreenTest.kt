package dev.tomng.alarme.feature.ringtones

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import dev.tomng.alarme.core.model.ringtone.RingtoneUris
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RingtonePickerScreenTest {

    @get:Rule val compose = createComposeRule()

    private val recorded = mutableListOf<String>()

    private fun row(id: Long, kind: RingtoneKind, title: String, uri: String) =
        CatalogRingtone(id, kind, uri, title, Instant.fromEpochMilliseconds(id))

    private fun callbacks() =
        RingtonePickerCallbacks(
            onSelect = { uri, title -> recorded += "select:$uri:$title" },
            onSetDefault = { recorded += "default:${it.id}" },
            onImportClick = { recorded += "import" },
            onRecordToggle = { recorded += "record" },
            onPreview = { recorded += "preview:$it" },
            onDelete = { recorded += "delete:${it.id}" },
            onDone = { recorded += "done" },
        )

    private fun setContent(
        mode: PickerMode = PickerMode.Alarm,
        selectedUri: String? = null,
        state: PickerState = PickerState(),
    ) {
        compose.setContent {
            RingtonePickerContent(
                state = state,
                mode = mode,
                selectedUri = selectedUri,
                callbacks = callbacks(),
            )
        }
    }

    @Test
    fun `silent and random are offered for an alarm`() {
        setContent(
            state =
                PickerState(
                    rows =
                        listOf(
                            row(1, RingtoneKind.BUNDLED, "Gentle chime", "android.resource://p/1"),
                        ),
                ),
        )

        compose.onNodeWithText("Silent").assertIsDisplayed()
        compose.onNodeWithText("Shuffle my sounds").assertIsDisplayed()
        compose.onNodeWithText("Gentle chime").assertIsDisplayed()
        compose.onNodeWithText("Included").assertIsDisplayed()
    }

    @Test
    fun `selecting silent reports the pseudo-uri`() {
        setContent()

        compose.onNodeWithTag("ringtone:silent").performClick()

        assertThat(recorded).containsExactly("select:uri_silent_ringtone:")
    }

    @Test
    fun `selecting a row reports its uri and title`() {
        setContent(
            state = PickerState(rows = listOf(row(7, RingtoneKind.MUSIC, "song", "file:///s.mp3"))),
        )

        compose.onNodeWithTag("ringtone:row:7").performClick()

        assertThat(recorded).containsExactly("select:file:///s.mp3:song")
    }

    @Test
    fun `device tones and my sounds each get a section`() {
        setContent(
            state =
                PickerState(
                    rows = listOf(row(9, RingtoneKind.RECORD, "Recording 1", "file:///r.m4a")),
                    deviceTones =
                        listOf(
                            row(-1, RingtoneKind.DEVICE_ALARM, "Over the horizon", "content://1"),
                        ),
                ),
        )

        compose.onNodeWithText("On this device").assertIsDisplayed()
        compose.onNodeWithText("Over the horizon").assertIsDisplayed()
        compose.onNodeWithTag("ringtone:list").performScrollToNode(hasTestTag("ringtone:row:9"))
        compose.onNodeWithTag("ringtone:row:9").assertIsDisplayed()
        compose.onNodeWithText("My sounds").assertIsDisplayed()
        compose.onNodeWithText("Recording 1").assertIsDisplayed()
    }

    @Test
    fun `my sounds can be deleted`() {
        setContent(
            state = PickerState(rows = listOf(row(9, RingtoneKind.MUSIC, "song", "file:///s.mp3"))),
        )

        compose.onNodeWithTag("ringtone:delete:9").performClick()

        assertThat(recorded).containsExactly("delete:9")
    }

    @Test
    fun `import and record route to their actions`() {
        setContent()

        compose.onNodeWithTag("ringtone:import").performClick()
        compose.onNodeWithTag("ringtone:record").performClick()

        assertThat(recorded).containsExactly("import", "record").inOrder()
    }

    @Test
    fun `default mode hides pseudo choices and offers set-as-default`() {
        setContent(
            mode = PickerMode.Default,
            state =
                PickerState(
                    rows =
                        listOf(
                            row(1, RingtoneKind.BUNDLED, "Gentle chime", "android.resource://p/1"),
                        ),
                ),
        )

        compose.onNodeWithText("Silent").assertDoesNotExist()
        compose.onNodeWithText("Shuffle my sounds").assertDoesNotExist()
        compose.onNodeWithTag("ringtone:row:1").performClick()

        assertThat(recorded).containsExactly("default:1")
    }

    @Test
    fun `done closes the picker`() {
        setContent()

        compose.onNodeWithTag("ringtone:done").performClick()

        assertThat(recorded).containsExactly("done")
    }

    @Test
    fun `the selected sound is marked`() {
        setContent(
            selectedUri = RingtoneUris.RANDOM,
        )

        compose.onNodeWithTag("ringtone:random").assertIsDisplayed()
    }
}
