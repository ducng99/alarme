package dev.tomng.alarme.core.data.mapper

import dev.tomng.alarme.core.data.dao.MissionPhraseJoinRow
import dev.tomng.alarme.core.data.entity.AlarmInfoEntity
import dev.tomng.alarme.core.data.entity.MissionInfoEntity
import dev.tomng.alarme.core.data.entity.TypingCustomPhraseEntity
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.CrescendoOption
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.alarm.MemoOption
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.alarm.WakeUpCheck
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.model.mission.WordBeatVolumeLevel
import kotlin.time.Instant

/** Persisted rows belonging to one alarm, as read back from the database. */
data class AlarmRowBundle(
    val info: AlarmInfoEntity,
    val missions: List<MissionInfoEntity>,
    val typingPhrases: List<MissionPhraseJoinRow> = emptyList(),
    val wordBeatRoundSets: List<Pair<Long, String>> = emptyList(), // missionId -> roundSetId
    val wordBeatVolumes: List<Pair<Long, String>> = emptyList(), // missionId -> volumeLevel
) {
    companion object {
        /**
         * Rebuilds a read bundle from a write bundle + the ids Room assigned to the mission rows.
         */
        fun from(
            write: AlarmWriteBundle,
            alarmId: Long,
            missionIdsBySortOrder: List<Long>,
        ): AlarmRowBundle {
            val missions =
                write.missions.mapIndexed { index, row ->
                    row.copy(id = missionIdsBySortOrder[index], alarmId = alarmId)
                }
            val typingPhrases =
                write.typingPhraseIdsByMissionIndex.flatMap { (index, phraseIds) ->
                    val missionId = missionIdsBySortOrder[index]
                    phraseIds.mapNotNull { phraseId ->
                        write.typingPhrases
                            .find { it.id == phraseId }
                            ?.let { phrase ->
                                MissionPhraseJoinRow(
                                    missionId = missionId,
                                    phraseId = phrase.id,
                                    phrase = phrase.phrase,
                                    type = phrase.type,
                                )
                            }
                    }
                }
            val roundSets =
                write.wordBeatRoundSetIdsByMissionIndex.flatMap { (index, ids) ->
                    ids.map { missionIdsBySortOrder[index] to it }
                }
            val volumes =
                write.wordBeatVolumeByMissionIndex.map { (index, level) ->
                    missionIdsBySortOrder[index] to level
                }
            return AlarmRowBundle(
                info = write.info.copy(id = alarmId),
                missions = missions,
                typingPhrases = typingPhrases,
                wordBeatRoundSets = roundSets,
                wordBeatVolumes = volumes,
            )
        }
    }
}

/**
 * Rows to write for one alarm. Mission ids are assigned by Room during insert, so ref tables are
 * addressed by the mission's index in [missions] and the repository patches them after `insertAll`
 * returns the real ids.
 */
data class AlarmWriteBundle(
    val info: AlarmInfoEntity,
    val missions: List<MissionInfoEntity>,
    val typingPhrases: List<TypingCustomPhraseEntity> = emptyList(),
    val typingPhraseIdsByMissionIndex: Map<Int, List<String>> = emptyMap(),
    val wordBeatRoundSetIdsByMissionIndex: Map<Int, List<String>> = emptyMap(),
    val wordBeatVolumeByMissionIndex: Map<Int, String> = emptyMap(),
)

/** Bidirectional mapping between the domain [Alarm] and its persisted rows. */
object AlarmEntityMapper {

    fun toEntity(alarm: Alarm, nextTimeMillis: Long): AlarmWriteBundle {
        val info =
            AlarmInfoEntity(
                id = alarm.id,
                type = alarm.type.name,
                emoji = alarm.emoji,
                name = alarm.name,
                hour = alarm.hour,
                minutes = alarm.minute,
                time = nextTimeMillis,
                enabled = alarm.enabled,
                daysOfWeek = alarm.daysOfWeek.bits,
                alert = alarm.ringtoneUri,
                volume = alarm.volumeRatio.toDouble(),
                vibrate = alarm.vibrate,
                label = alarm.memoOption.memo,
                isSnoozeEnable = alarm.snoozeOption.enabled,
                snoozeDuration = alarm.snoozeOption.minutes.toDouble(),
                snoozeLimitCount = alarm.snoozeOption.limit,
                wakeUpCheck = alarm.wakeUpCheck.minutes,
                isCrescendoEnable = alarm.crescendoOption.enabled,
                crescendoDuration = alarm.crescendoOption.seconds,
                backupSound = alarm.backupSoundEnabled,
                backupSoundUri = alarm.backupSoundUri,
                timePressure = alarm.timeAnnouncementEnabled,
                labelReminder = alarm.memoOption.ttsEnabled,
                isSkipped = alarm.isSkipped,
                randomMissionCount = alarm.randomMissionCount,
                createdAt = alarm.createdAt.toEpochMilliseconds(),
                updatedAt = alarm.updatedAt.toEpochMilliseconds(),
            )

        val missions = mutableListOf<MissionInfoEntity>()
        val typingPhrases = mutableListOf<TypingCustomPhraseEntity>()
        val typingPhraseIds = mutableMapOf<Int, List<String>>()
        val roundSetIds = mutableMapOf<Int, List<String>>()
        val volumes = mutableMapOf<Int, String>()

        alarm.missions.forEachIndexed { index, mission ->
            var row =
                MissionInfoEntity(
                    id = 0L,
                    alarmId = alarm.id,
                    missionType = typeOf(mission).name,
                    sortOrder = index,
                    timeLimitSeconds = mission.timeLimitSeconds,
                )
            when (mission) {
                is Mission.Math ->
                    row = row.copy(numOfRounds = mission.rounds, difficulty = mission.difficulty)
                is Mission.Memory ->
                    row = row.copy(numOfRounds = mission.rounds, difficulty = mission.difficulty)
                is Mission.Shake -> row = row.copy(numOfRounds = mission.count)
                is Mission.Step -> row = row.copy(numOfRounds = mission.count)
                is Mission.Squat -> row = row.copy(numOfRounds = mission.count)
                is Mission.Typing -> {
                    val phraseIds =
                        mission.typingSentences.mapIndexed { sentenceIndex, sentence ->
                            val phraseId = "a${alarm.id}m${index}s$sentenceIndex"
                            typingPhrases +=
                                TypingCustomPhraseEntity(
                                    id = phraseId,
                                    phrase = sentence.sentence,
                                    type = sentence.type.name,
                                    createdTime = alarm.updatedAt.toEpochMilliseconds(),
                                )
                            phraseId
                        }
                    typingPhraseIds[index] = phraseIds
                    row =
                        row.copy(
                            numOfRounds = mission.rounds,
                            typingType = mission.typingSentences.firstOrNull()?.type?.name ?: "",
                        )
                }
                is Mission.Barcode ->
                    row = row.copy(barcodeId = mission.barcodeId, barcodeQR = mission.barcodeValue)
                is Mission.Photo -> row = row.copy(photoPath = mission.photoPath)
                is Mission.WordBeat -> {
                    roundSetIds[index] = mission.selectedRoundSetIds
                    volumes[index] = mission.volumeLevel.name
                    row = row.copy(numOfRounds = mission.rounds, language = mission.language.name)
                }
            }
            missions += row
        }

        return AlarmWriteBundle(
            info = info,
            missions = missions,
            typingPhrases = typingPhrases,
            typingPhraseIdsByMissionIndex = typingPhraseIds,
            wordBeatRoundSetIdsByMissionIndex = roundSetIds,
            wordBeatVolumeByMissionIndex = volumes,
        )
    }

    fun toDomain(bundle: AlarmRowBundle): Alarm {
        val info = bundle.info
        val missions =
            bundle.missions.sortedBy { it.sortOrder }.mapNotNull { row -> missionFrom(row, bundle) }
        return Alarm(
            id = info.id,
            type = enumOrDefault(info.type, AlarmType.NORMAL),
            emoji = info.emoji,
            name = info.name,
            hour = info.hour,
            minute = info.minutes,
            daysOfWeek = DaysOfWeek(info.daysOfWeek),
            enabled = info.enabled,
            missions = missions,
            randomMissionCount = info.randomMissionCount,
            ringtoneUri = info.alert,
            volumeRatio = info.volume.toFloat(),
            vibrate = info.vibrate,
            memoOption = MemoOption(memo = info.label, ttsEnabled = info.labelReminder),
            snoozeOption =
                SnoozeOption(
                    enabled = info.isSnoozeEnable,
                    minutes = info.snoozeDuration.toInt(),
                    limit = info.snoozeLimitCount,
                ),
            crescendoOption =
                CrescendoOption(enabled = info.isCrescendoEnable, seconds = info.crescendoDuration),
            backupSoundEnabled = info.backupSound,
            backupSoundUri = info.backupSoundUri,
            timeAnnouncementEnabled = info.timePressure,
            wakeUpCheck = WakeUpCheck(info.wakeUpCheck),
            isSkipped = info.isSkipped,
            nextOccurrence = if (info.time >= 0) Instant.fromEpochMilliseconds(info.time) else null,
            createdAt = Instant.fromEpochMilliseconds(info.createdAt),
            updatedAt = Instant.fromEpochMilliseconds(info.updatedAt),
        )
    }

    private fun missionFrom(row: MissionInfoEntity, bundle: AlarmRowBundle): Mission? {
        val type = enumOrNull<MissionType>(row.missionType) ?: return null
        return when (type) {
            MissionType.MATH ->
                Mission.Math(
                    timeLimitSeconds = row.timeLimitSeconds,
                    rounds = row.numOfRounds,
                    difficulty = row.difficulty,
                )
            MissionType.MEMORY ->
                Mission.Memory(
                    timeLimitSeconds = row.timeLimitSeconds,
                    rounds = row.numOfRounds,
                    difficulty = row.difficulty,
                )
            MissionType.SHAKE ->
                Mission.Shake(timeLimitSeconds = row.timeLimitSeconds, count = row.numOfRounds)
            MissionType.STEP ->
                Mission.Step(timeLimitSeconds = row.timeLimitSeconds, count = row.numOfRounds)
            MissionType.SQUAT ->
                Mission.Squat(timeLimitSeconds = row.timeLimitSeconds, count = row.numOfRounds)
            MissionType.TYPING ->
                Mission.Typing(
                    timeLimitSeconds = row.timeLimitSeconds,
                    rounds = row.numOfRounds,
                    typingSentences =
                        bundle.typingPhrases
                            .filter { it.missionId == row.id }
                            .map { phrase ->
                                TypingSentence(
                                    type = enumOrDefault(phrase.type, TypingSentenceType.CUSTOM),
                                    sentence = phrase.phrase,
                                )
                            },
                )
            MissionType.BARCODE ->
                Mission.Barcode(
                    timeLimitSeconds = row.timeLimitSeconds,
                    barcodeId = row.barcodeId,
                    barcodeValue = row.barcodeQR,
                )
            MissionType.PHOTO ->
                Mission.Photo(timeLimitSeconds = row.timeLimitSeconds, photoPath = row.photoPath)
            MissionType.WORD_BEAT ->
                Mission.WordBeat(
                    timeLimitSeconds = row.timeLimitSeconds,
                    rounds = row.numOfRounds,
                    language = enumOrDefault(row.language, WordBeatLanguage.ENGLISH),
                    selectedRoundSetIds =
                        bundle.wordBeatRoundSets.filter { it.first == row.id }.map { it.second },
                    volumeLevel =
                        bundle.wordBeatVolumes
                            .firstOrNull { it.first == row.id }
                            ?.let { enumOrDefault(it.second, WordBeatVolumeLevel.MEDIUM) }
                            ?: WordBeatVolumeLevel.MEDIUM,
                )
        }
    }

    private fun typeOf(mission: Mission): MissionType =
        when (mission) {
            is Mission.Math -> MissionType.MATH
            is Mission.Memory -> MissionType.MEMORY
            is Mission.Shake -> MissionType.SHAKE
            is Mission.Step -> MissionType.STEP
            is Mission.Squat -> MissionType.SQUAT
            is Mission.Typing -> MissionType.TYPING
            is Mission.Barcode -> MissionType.BARCODE
            is Mission.Photo -> MissionType.PHOTO
            is Mission.WordBeat -> MissionType.WORD_BEAT
        }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    enumOrNull<T>(name) ?: default

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = runCatching {
    enumValueOf<T>(name)
}
    .getOrNull()
