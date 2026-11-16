package dev.tomng.alarme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The back stack walks the hierarchy the user drilled into, and survives being saved. */
class MainNavigationTest {

    @Test
    fun `a result waits on the bus until its consumer takes it`() {
        val bus = ResultBus()
        val target = PickerTarget.EditorSound(3, currentUri = "old")
        bus.send(SoundResult(target.slot, "a"))
        bus.send(SoundResult(target.slot, "b"))

        assertThat(bus.take(PickerTarget.EditorBackupSound(3).slot)).isNull()
        assertThat(bus.take(PickerTarget.EditorSound(3).slot)?.uri).isEqualTo("b")
        assertThat(bus.take(target.slot)).isNull()
    }

    @Test
    fun `the stack starts at the list and back from the root leaves the app`() {
        val nav = NavState()

        assertThat(nav.current).isEqualTo(Screen.List)
        assertThat(nav.canPop).isFalse()
        assertThat(nav.pop()).isFalse()
    }

    @Test
    fun `push goes deeper and pop returns to the previous screen`() {
        val nav = NavState()
        nav.push(Screen.Settings)
        nav.push(Screen.Backup)

        assertThat(nav.previous).isEqualTo(Screen.Settings)
        assertThat(nav.pop()).isTrue()
        assertThat(nav.current).isEqualTo(Screen.Settings)
    }

    @Test
    fun `pushing the current screen again is ignored`() {
        val nav = NavState()
        nav.push(Screen.Settings)
        nav.push(Screen.Settings)

        assertThat(nav.stack).containsExactly(Screen.List, Screen.Settings).inOrder()
    }

    @Test
    fun `pushing a screen already in the stack pops back to it`() {
        val nav = NavState()
        nav.push(Screen.Settings)
        nav.push(Screen.Picker(PickerTarget.Default))
        nav.push(Screen.Settings)

        assertThat(nav.stack).containsExactly(Screen.List, Screen.Settings).inOrder()
    }

    @Test
    fun `replace swaps the top screen`() {
        val nav = NavState()
        nav.push(Screen.Onboarding)
        nav.replaceTop(Screen.List)

        assertThat(nav.stack).containsExactly(Screen.List)
    }

    @Test
    fun `the stack round-trips through its saved form`() {
        val nav = NavState()
        nav.push(Screen.Editor(7))
        nav.push(Screen.Picker(PickerTarget.EditorSound(7, "content://media/a:b?x=1")))

        val restored = NavState.restore(nav.save())

        assertThat(restored.stack).isEqualTo(nav.stack)
    }

    @Test
    fun `every screen has a distinct key`() {
        val screens =
            listOf(
                Screen.List,
                Screen.Settings,
                Screen.Permissions,
                Screen.Diagnostics,
                Screen.Optimize,
                Screen.Backup,
                Screen.Onboarding,
                Screen.Editor(1),
                Screen.Editor(2),
                Screen.Picker(PickerTarget.Default),
                Screen.Picker(PickerTarget.ForAlarm(1)),
                Screen.Picker(PickerTarget.EditorSound(1)),
                Screen.Picker(PickerTarget.EditorBackupSound(1)),
            )

        assertThat(screens.map { it.key }.toSet()).hasSize(screens.size)
    }
}
