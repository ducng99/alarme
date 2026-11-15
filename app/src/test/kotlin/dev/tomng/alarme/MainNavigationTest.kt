package dev.tomng.alarme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** System back must walk the same hierarchy as the on-screen back arrows. */
class MainNavigationTest {

    @Test
    fun `list is the root so back leaves the app`() {
        assertThat(parentScreen(Screen.List)).isNull()
    }

    @Test
    fun `settings goes back to the list`() {
        assertThat(parentScreen(Screen.Settings)).isEqualTo(Screen.List)
    }

    @Test
    fun `settings children go back to settings`() {
        assertThat(parentScreen(Screen.Diagnostics)).isEqualTo(Screen.Settings)
        assertThat(parentScreen(Screen.Optimize)).isEqualTo(Screen.Settings)
        assertThat(parentScreen(Screen.Backup)).isEqualTo(Screen.Settings)
    }

    @Test
    fun `picker and editor go back to the list`() {
        assertThat(parentScreen(Screen.Picker(PickerTarget.Default))).isEqualTo(Screen.List)
        assertThat(parentScreen(Screen.Editor(7L))).isEqualTo(Screen.List)
    }
}
