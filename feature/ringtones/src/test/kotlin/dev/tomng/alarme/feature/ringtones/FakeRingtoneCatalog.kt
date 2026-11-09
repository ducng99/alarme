package dev.tomng.alarme.feature.ringtones

import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRingtoneCatalog : RingtoneRepository {
    val rows = mutableListOf<CatalogRingtone>()
    private val state = MutableStateFlow<List<CatalogRingtone>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<CatalogRingtone>> = state

    override suspend fun getById(id: Long): CatalogRingtone? = rows.firstOrNull { it.id == id }

    override suspend fun getAll(): List<CatalogRingtone> = rows.toList()

    override suspend fun insert(kind: RingtoneKind, uri: String, title: String): Long {
        val row = CatalogRingtone(nextId, kind, uri, title, Instant.fromEpochMilliseconds(nextId))
        nextId++
        rows += row
        state.value = rows.toList()
        return row.id
    }

    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
        state.value = rows.toList()
    }

    override suspend fun seedBundled(entries: List<Pair<String, String>>) {
        if (rows.none { it.kind == RingtoneKind.BUNDLED }) {
            entries.forEach { (title, uri) -> insert(RingtoneKind.BUNDLED, uri, title) }
        }
    }
}
