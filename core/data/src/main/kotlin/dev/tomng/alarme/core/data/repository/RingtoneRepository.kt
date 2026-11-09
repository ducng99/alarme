package dev.tomng.alarme.core.data.repository

import dev.tomng.alarme.core.data.dao.RingtoneDao
import dev.tomng.alarme.core.data.entity.RingtoneEntity
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Catalogue of selectable sounds (bundled, device, imported, recorded). */
interface RingtoneRepository {

    fun observeAll(): Flow<List<CatalogRingtone>>

    suspend fun getById(id: Long): CatalogRingtone?

    /** One-shot read of the whole catalogue (backup export). */
    suspend fun getAll(): List<CatalogRingtone>

    suspend fun insert(kind: RingtoneKind, uri: String, title: String): Long

    suspend fun delete(id: Long)

    /** Registers the bundled tones once; later calls are no-ops. */
    suspend fun seedBundled(entries: List<Pair<String, String>>)
}

class RingtoneRepositoryImpl(private val dao: RingtoneDao) : RingtoneRepository {

    override fun observeAll(): Flow<List<CatalogRingtone>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getById(id: Long): CatalogRingtone? = dao.getById(id)?.toModel()

    override suspend fun getAll(): List<CatalogRingtone> = dao.getAll().map { it.toModel() }

    override suspend fun insert(kind: RingtoneKind, uri: String, title: String): Long =
        dao.insert(
            RingtoneEntity(
                id = 0L,
                type = kind.name,
                uri = uri,
                title = title,
                createdAt = epochMillis(),
            ),
        )

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun seedBundled(entries: List<Pair<String, String>>) {
        if (dao.firstByType(RingtoneKind.BUNDLED.name) != null) return
        entries.forEach { (title, uri) -> insert(RingtoneKind.BUNDLED, uri, title) }
    }

    private fun RingtoneEntity.toModel() =
        CatalogRingtone(
            id = id,
            kind = runCatching { RingtoneKind.valueOf(type) }.getOrDefault(RingtoneKind.MUSIC),
            uri = uri,
            title = title,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
        )
}

private fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
