package dev.tomng.alarme.core.alarmengine.ring

import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.model.ringtone.RingtoneUris
import kotlinx.coroutines.flow.first

/**
 * Decides which sound actually plays at ring time. Pseudo-choices are expanded (random → a
 * catalogue pick), the app-wide default preference fills a missing choice, and local files that
 * vanished since selection fall back to the system default rather than failing silently.
 *
 * Returns the effective uri, [RingtoneUris.SILENT] for a silent alarm, or `null` when the caller
 * should fall back to the system default alarm sound.
 */
fun interface RingtoneResolver {
    suspend fun resolve(ringtoneUri: String?): String?
}

class CatalogRingtoneResolver(
    private val catalog: RingtoneRepository,
    private val settings: SettingsRepository,
    private val fileExists: (uri: String) -> Boolean,
    private val pick: (count: Int) -> Int,
) : RingtoneResolver {
    override suspend fun resolve(ringtoneUri: String?): String? =
        when {
            ringtoneUri == RingtoneUris.SILENT -> RingtoneUris.SILENT
            ringtoneUri == RingtoneUris.RANDOM -> randomPick() ?: defaultPreference()
            ringtoneUri != null -> validated(ringtoneUri)
            else -> defaultPreference()
        }

    private suspend fun randomPick(): String? {
        val rows = catalog.observeAll().first()
        if (rows.isEmpty()) return null
        val chosen = rows[pick(rows.size).coerceIn(0, rows.size - 1)]
        return validated(chosen.uri)
    }

    private suspend fun defaultPreference(): String? {
        val id = settings.settings.first().defaultRingtoneId ?: return null
        val row = catalog.getById(id) ?: return null
        return validated(row.uri)
    }

    private fun validated(uri: String): String? =
        if (uri.startsWith("file://") && !fileExists(uri)) null else uri
}
