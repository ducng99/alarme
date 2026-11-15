package dev.tomng.alarme.feature.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType

@Composable
internal fun missionTypeLabel(mission: Mission): String =
    missionTypeLabel(
        when (mission) {
            is Mission.Math -> MissionType.MATH
            is Mission.Memory -> MissionType.MEMORY
            is Mission.Shake -> MissionType.SHAKE
            is Mission.Step -> MissionType.STEP
            is Mission.Squat -> MissionType.SQUAT
            is Mission.Typing -> MissionType.TYPING
            is Mission.Barcode -> MissionType.BARCODE
            is Mission.Photo -> MissionType.PHOTO
            is Mission.ImageRecognition -> MissionType.IMAGE_RECOGNITION
            is Mission.WordBeat -> MissionType.WORD_BEAT
        },
    )

@Composable
internal fun missionTypeLabel(type: MissionType): String =
    stringResource(
        when (type) {
            MissionType.MATH -> R.string.mission_math
            MissionType.MEMORY -> R.string.mission_memory
            MissionType.SHAKE -> R.string.mission_shake
            MissionType.STEP -> R.string.mission_step
            MissionType.SQUAT -> R.string.mission_squat
            MissionType.TYPING -> R.string.mission_typing
            MissionType.BARCODE -> R.string.mission_barcode
            MissionType.PHOTO -> R.string.mission_photo
            MissionType.IMAGE_RECOGNITION -> R.string.mission_image_recognition
            MissionType.WORD_BEAT -> R.string.mission_word_beat
        },
    )

internal fun missionIcon(mission: Mission): ImageVector =
    missionIcon(
        when (mission) {
            is Mission.Math -> MissionType.MATH
            is Mission.Memory -> MissionType.MEMORY
            is Mission.Shake -> MissionType.SHAKE
            is Mission.Step -> MissionType.STEP
            is Mission.Squat -> MissionType.SQUAT
            is Mission.Typing -> MissionType.TYPING
            is Mission.Barcode -> MissionType.BARCODE
            is Mission.Photo -> MissionType.PHOTO
            is Mission.ImageRecognition -> MissionType.IMAGE_RECOGNITION
            is Mission.WordBeat -> MissionType.WORD_BEAT
        },
    )

internal fun missionIcon(type: MissionType): ImageVector =
    when (type) {
        MissionType.MATH -> Icons.Outlined.Calculate
        MissionType.MEMORY -> Icons.Outlined.Psychology
        MissionType.SHAKE -> Icons.Outlined.Accessibility
        MissionType.STEP -> Icons.AutoMirrored.Outlined.DirectionsWalk
        MissionType.SQUAT -> Icons.Outlined.FitnessCenter
        MissionType.TYPING -> Icons.Outlined.Keyboard
        MissionType.BARCODE -> Icons.Outlined.QrCodeScanner
        MissionType.PHOTO -> Icons.Outlined.CameraAlt
        MissionType.IMAGE_RECOGNITION -> Icons.Outlined.Image
        MissionType.WORD_BEAT -> Icons.Outlined.RecordVoiceOver
    }

/** Whether the mission has everything it needs to run. */
internal fun Mission.isComplete(): Boolean =
    when (this) {
        is Mission.Math -> true
        is Mission.Memory -> true
        is Mission.Shake -> count > 0
        is Mission.Step -> count > 0
        is Mission.Squat -> count > 0
        is Mission.Typing -> typingSentences.isNotEmpty()
        is Mission.Barcode -> barcodeValue.isNotBlank()
        is Mission.Photo -> photoPath.isNotBlank()
        is Mission.ImageRecognition -> objects.isNotEmpty()
        is Mission.WordBeat -> selectedRoundSetIds.isNotEmpty()
    }

@Composable
internal fun missionSummary(mission: Mission): String =
    when (mission) {
        is Mission.Math ->
            stringResource(R.string.summary_rounds_level, mission.rounds, mission.difficulty)
        is Mission.Memory ->
            stringResource(R.string.summary_rounds_level, mission.rounds, mission.difficulty)
        is Mission.Shake -> stringResource(R.string.summary_count, mission.count)
        is Mission.Step -> stringResource(R.string.summary_count, mission.count)
        is Mission.Squat -> stringResource(R.string.summary_count, mission.count)
        is Mission.Typing ->
            stringResource(R.string.summary_sentences, mission.typingSentences.size)
        is Mission.Barcode -> stringResource(R.string.summary_barcode)
        is Mission.Photo -> stringResource(R.string.summary_photo)
        is Mission.ImageRecognition ->
            stringResource(R.string.summary_objects, mission.objects.size)
        is Mission.WordBeat ->
            stringResource(
                R.string.summary_word_beat,
                stringResource(languageLabel(mission.language)),
                mission.rounds,
            )
    }

internal fun languageLabel(language: dev.tomng.alarme.core.model.mission.WordBeatLanguage): Int =
    when (language) {
        dev.tomng.alarme.core.model.mission.WordBeatLanguage.ENGLISH -> R.string.language_english
        dev.tomng.alarme.core.model.mission.WordBeatLanguage.KOREAN -> R.string.language_korean
        dev.tomng.alarme.core.model.mission.WordBeatLanguage.JAPANESE -> R.string.language_japanese
    }
