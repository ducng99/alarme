package dev.tomng.alarme.core.alarmengine.ring

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import dev.tomng.alarme.core.model.ringtone.RingtoneUris
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RingtoneResolverTest {

    private val catalog = FakeCatalog()
    private val settings = RingtoneSettingsFake()
    private val resolver =
        CatalogRingtoneResolver(
            catalog = catalog,
            settings = settings,
            fileExists = { uri -> !uri.contains("missing") },
            pick = { n -> n - 1 },
        )

    @Test
    fun `silent stays silent`() = runTest {
        assertThat(resolver.resolve(RingtoneUris.SILENT)).isEqualTo(RingtoneUris.SILENT)
    }

    @Test
    fun `no choice falls back to the default ringtone preference`() = runTest {
        catalog.rows += row(4, "file:///store/default.mp3")
        settings.defaultRingtoneId = 4

        assertThat(resolver.resolve(null)).isEqualTo("file:///store/default.mp3")
    }

    @Test
    fun `a dangling default preference falls through to the system default`() = runTest {
        settings.defaultRingtoneId = 99

        assertThat(resolver.resolve(null)).isNull()
    }

    @Test
    fun `random picks from the catalogue`() = runTest {
        catalog.rows += row(1, "file:///store/a.mp3")
        catalog.rows += row(2, "file:///store/b.mp3")
        catalog.rows += row(3, "file:///store/c.mp3")

        assertThat(resolver.resolve(RingtoneUris.RANDOM)).isEqualTo("file:///store/c.mp3")
    }

    @Test
    fun `random over an empty catalogue falls back to the default preference`() = runTest {
        catalog.rows += row(4, "file:///store/default.mp3")
        settings.defaultRingtoneId = 4

        assertThat(resolver.resolve(RingtoneUris.RANDOM)).isEqualTo("file:///store/default.mp3")
    }

    @Test
    fun `a vanished local file is not passed to the player`() = runTest {
        assertThat(resolver.resolve("file:///store/missing.mp3")).isNull()
    }

    @Test
    fun `existing local files and remote uris pass through`() = runTest {
        assertThat(resolver.resolve("file:///store/present.mp3"))
            .isEqualTo("file:///store/present.mp3")
        assertThat(resolver.resolve("content://media/alarms/7"))
            .isEqualTo("content://media/alarms/7")
        assertThat(resolver.resolve("android.resource://pkg/raw/tone"))
            .isEqualTo("android.resource://pkg/raw/tone")
    }

    @Test
    fun `the default preference itself is validated`() = runTest {
        catalog.rows += row(4, "file:///store/missing.mp3")
        settings.defaultRingtoneId = 4

        assertThat(resolver.resolve(null)).isNull()
    }

    private fun row(id: Long, uri: String) =
        CatalogRingtone(id, RingtoneKind.MUSIC, uri, "t$id", Instant.fromEpochMilliseconds(id))
}

private class RingtoneSettingsFake : dev.tomng.alarme.core.data.repository.SettingsRepository {
    private val state =
        MutableStateFlow(dev.tomng.alarme.core.data.settings.AppSettings(defaultRingtoneId = null))

    var defaultRingtoneId: Long?
        get() = state.value.defaultRingtoneId
        set(value) {
            state.value = state.value.copy(defaultRingtoneId = value)
        }

    override val settings:
        kotlinx.coroutines.flow.Flow<dev.tomng.alarme.core.data.settings.AppSettings> =
        state

    override suspend fun setMute(enabled: Boolean, limit: Int) = Unit

    override suspend fun setAutoDismiss(
        option: dev.tomng.alarme.core.model.alarm.AutoDismissOption,
    ) = Unit

    override suspend fun setAudioOutput(
        output: dev.tomng.alarme.core.data.settings.AudioOutputPreference,
    ) = Unit

    override suspend fun setPreventPowerOff(enabled: Boolean) = Unit

    override suspend fun setPreventUninstall(enabled: Boolean) = Unit

    override suspend fun setDefaultRingtoneId(id: Long?) {
        defaultRingtoneId = id
    }

    override suspend fun deviceId(): String = "dev"
}

private class FakeCatalog : RingtoneRepository {
    val rows = mutableListOf<CatalogRingtone>()

    override fun observeAll(): Flow<List<CatalogRingtone>> =
        kotlinx.coroutines.flow.flow { emit(rows.toList()) }

    override suspend fun getById(id: Long): CatalogRingtone? = rows.firstOrNull { it.id == id }

    override suspend fun getAll(): List<CatalogRingtone> = rows.toList()

    override suspend fun insert(kind: RingtoneKind, uri: String, title: String): Long = 0L

    override suspend fun delete(id: Long) = Unit

    override suspend fun seedBundled(entries: List<Pair<String, String>>) = Unit
}
