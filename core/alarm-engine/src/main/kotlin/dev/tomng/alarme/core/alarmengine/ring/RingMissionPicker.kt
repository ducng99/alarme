package dev.tomng.alarme.core.alarmengine.ring

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.RandomMissions
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Resolves the missions one ring runs. Random-mission alarms pick once per occurrence and keep that
 * pick through snoozes and service redeliveries, so failing or snoozing never re-rolls the
 * missions. Schedule ids repeat across days for the same alarm time, so the pick is dropped via
 * [forget] once the ring ends.
 */
class RingMissionPicker(
    private val store: RandomMissionStore,
    private val random: Random,
) {

    fun pick(alarm: Alarm, scheduleId: String?): Alarm {
        val count = alarm.randomMissionCount ?: return alarm
        if (alarm.missions.isEmpty()) return alarm
        val stored = store.load()?.takeIf { it.matches(alarm, scheduleId) }
        val indices =
            stored?.indices ?: RandomMissions.pickIndices(alarm.missions.size, count, random)
        if (stored == null && scheduleId != null) {
            store.save(
                RandomMissionPick(
                    alarmId = alarm.id,
                    chainSignature = chainSignature(alarm),
                    scheduleId = scheduleId,
                    indices = indices,
                ),
            )
        }
        return alarm.copy(missions = indices.map { alarm.missions[it] })
    }

    fun forget() {
        store.clear()
    }

    private fun RandomMissionPick.matches(alarm: Alarm, scheduleId: String?): Boolean =
        scheduleId != null &&
            this.scheduleId == scheduleId &&
            alarmId == alarm.id &&
            chainSignature == chainSignature(alarm) &&
            indices.all { it in alarm.missions.indices }

    /** Changes when the user edits the chain or the count, so a stale pick is never reused. */
    private fun chainSignature(alarm: Alarm): Int =
        Json.encodeToString(ListSerializer(Mission.serializer()), alarm.missions).hashCode() * 31 +
            (alarm.randomMissionCount ?: 0)
}

/** The missions picked for one occurrence of a random-mission alarm. */
data class RandomMissionPick(
    val alarmId: Long,
    val chainSignature: Int,
    val scheduleId: String,
    val indices: List<Int>,
)

interface RandomMissionStore {
    fun save(pick: RandomMissionPick)

    fun load(): RandomMissionPick?

    fun clear()
}

@Singleton
class DeviceProtectedRandomMissionStore
@Inject
constructor(
    @ApplicationContext context: Context,
) : RandomMissionStore {

    private val prefs =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun save(pick: RandomMissionPick) {
        prefs
            .edit()
            .putLong(KEY_ALARM_ID, pick.alarmId)
            .putInt(KEY_SIGNATURE, pick.chainSignature)
            .putString(KEY_SCHEDULE_ID, pick.scheduleId)
            .putString(KEY_INDICES, pick.indices.joinToString(","))
            .commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    override fun load(): RandomMissionPick? {
        val scheduleId = prefs.getString(KEY_SCHEDULE_ID, null) ?: return null
        val indices =
            prefs.getString(KEY_INDICES, null)?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?: return null
        return RandomMissionPick(
            alarmId = prefs.getLong(KEY_ALARM_ID, -1L),
            chainSignature = prefs.getInt(KEY_SIGNATURE, 0),
            scheduleId = scheduleId,
            indices = indices,
        )
    }

    private companion object {
        const val FILE_NAME = "PrefRandomMissions"
        const val KEY_ALARM_ID = "ALARM_ID"
        const val KEY_SIGNATURE = "CHAIN_SIGNATURE"
        const val KEY_SCHEDULE_ID = "SCHEDULE_ID"
        const val KEY_INDICES = "INDICES"
    }
}
