package dev.tomng.alarme.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.model.mission.WordBeatVolumeLevel

/** Per-type mission configuration. The dialog edits a copy and hands back the finished mission. */
@Composable
fun MissionConfigDialog(
    mission: Mission,
    onDismiss: () -> Unit,
    onConfirm: (Mission) -> Unit,
) {
    var current by remember(mission) { mutableStateOf(mission) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("mission_config"),
        title = { Text(missionTypeLabel(current)) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current) },
                modifier = Modifier.testTag("mission_config_ok"),
            ) {
                Text(stringResource(R.string.editor_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        },
        text = {
            Column(
                modifier =
                    Modifier.heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MissionFields(mission = current, onChange = { current = it })
                StepperRow(
                    label = stringResource(R.string.config_time_limit),
                    value = current.timeLimitSeconds,
                    tag = "mission_config_time_limit",
                    onChange = { current = current.withTimeLimit(it) },
                    step = 30,
                    min = 0,
                    max = MAX_TIME_LIMIT,
                )
            }
        },
    )
}

@Composable
private fun MissionFields(mission: Mission, onChange: (Mission) -> Unit) {
    when (mission) {
        is Mission.Math -> {
            StepperRow(
                label = stringResource(R.string.config_rounds),
                value = mission.rounds,
                tag = "mission_config_rounds",
                onChange = { onChange(mission.copy(rounds = it)) },
                step = 1,
                min = 1,
                max = MAX_ROUNDS,
            )
            StepperRow(
                label = stringResource(R.string.config_difficulty),
                value = mission.difficulty,
                tag = "mission_config_difficulty",
                onChange = { onChange(mission.copy(difficulty = it)) },
                step = 1,
                min = Mission.Math.MIN_DIFFICULTY,
                max = Mission.Math.MAX_DIFFICULTY,
            )
        }
        is Mission.Memory -> {
            StepperRow(
                label = stringResource(R.string.config_rounds),
                value = mission.rounds,
                tag = "mission_config_rounds",
                onChange = { onChange(mission.copy(rounds = it)) },
                step = 1,
                min = 1,
                max = MAX_ROUNDS,
            )
            StepperRow(
                label = stringResource(R.string.config_difficulty),
                value = mission.difficulty,
                tag = "mission_config_difficulty",
                onChange = { onChange(mission.copy(difficulty = it)) },
                step = 1,
                min = Mission.Memory.MIN_DIFFICULTY,
                max = Mission.Memory.MAX_DIFFICULTY,
            )
        }
        is Mission.Shake ->
            CountStepper(mission.count, "mission_config_count") {
                onChange(mission.copy(count = it))
            }
        is Mission.Step ->
            CountStepper(mission.count, "mission_config_count") {
                onChange(mission.copy(count = it))
            }
        is Mission.Squat ->
            CountStepper(mission.count, "mission_config_count") {
                onChange(mission.copy(count = it))
            }
        is Mission.Typing -> TypingFields(mission, onChange)
        is Mission.Barcode ->
            OutlinedTextField(
                value = mission.barcodeValue,
                onValueChange = { onChange(mission.copy(barcodeValue = it)) },
                label = { Text(stringResource(R.string.config_barcode)) },
                placeholder = { Text(stringResource(R.string.config_barcode_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("mission_config_barcode"),
            )
        is Mission.Photo ->
            OutlinedTextField(
                value = mission.photoPath,
                onValueChange = { onChange(mission.copy(photoPath = it)) },
                label = { Text(stringResource(R.string.config_photo)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("mission_config_photo"),
            )
        is Mission.ImageRecognition -> ObjectChips(mission, onChange)
        is Mission.WordBeat -> WordBeatFields(mission, onChange)
    }
}

@Composable
private fun CountStepper(count: Int, tag: String, onChange: (Int) -> Unit) {
    StepperRow(
        label = stringResource(R.string.config_count),
        value = count,
        tag = tag,
        onChange = onChange,
        step = 5,
        min = 1,
        max = MAX_COUNT,
    )
}

@Composable
private fun TypingFields(mission: Mission.Typing, onChange: (Mission.Typing) -> Unit) {
    var custom by remember(mission) { mutableStateOf(mission.isCustom()) }
    Text(
        text = stringResource(R.string.config_sentences),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TypingSentenceType.entries.forEach { type ->
            FilterChip(
                selected = !custom && mission.typingSentences.firstOrNull()?.type == type,
                onClick = {
                    custom = type == TypingSentenceType.CUSTOM
                    onChange(mission.copy(typingSentences = TypingSentenceCatalog.pack(type)))
                },
                label = { Text(stringResource(type.labelRes())) },
                modifier = Modifier.testTag("mission_config_sentence:${type.name}"),
            )
        }
    }
    if (custom) {
        OutlinedTextField(
            value = mission.typingSentences.joinToString("\n") { it.sentence },
            onValueChange = { text ->
                onChange(
                    mission.copy(
                        typingSentences =
                            text
                                .lines()
                                .filter { it.isNotBlank() }
                                .map { TypingSentence(TypingSentenceType.CUSTOM, it.trim()) },
                    ),
                )
            },
            label = { Text(stringResource(R.string.config_custom_sentences)) },
            modifier = Modifier.fillMaxWidth().testTag("mission_config_custom_sentences"),
        )
    }
    StepperRow(
        label = stringResource(R.string.config_rounds),
        value = mission.rounds,
        tag = "mission_config_rounds",
        onChange = { onChange(mission.copy(rounds = it)) },
        step = 1,
        min = 1,
        max = MAX_ROUNDS,
    )
}

@Composable
private fun ObjectChips(
    mission: Mission.ImageRecognition,
    onChange: (Mission.ImageRecognition) -> Unit,
) {
    Text(text = stringResource(R.string.config_objects), style = MaterialTheme.typography.bodyLarge)
    Column(modifier = Modifier.fillMaxWidth()) {
        ImageRecognitionObject.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { obj ->
                    FilterChip(
                        selected = obj in mission.objects,
                        onClick = {
                            val objects =
                                if (obj in mission.objects) mission.objects - obj
                                else mission.objects + obj
                            onChange(mission.copy(objects = objects))
                        },
                        label = { Text(obj.displayName()) },
                        modifier = Modifier.testTag("mission_config_object:${obj.name}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordBeatFields(mission: Mission.WordBeat, onChange: (Mission.WordBeat) -> Unit) {
    Text(
        text = stringResource(R.string.config_language),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WordBeatLanguage.entries.forEach { language ->
            FilterChip(
                selected = mission.language == language,
                onClick = {
                    onChange(
                        mission.copy(
                            language = language,
                            selectedRoundSetIds = MissionDefaults.roundSetIds(language),
                        ),
                    )
                },
                label = { Text(stringResource(languageLabel(language))) },
                modifier = Modifier.testTag("mission_config_language:${language.name}"),
            )
        }
    }
    StepperRow(
        label = stringResource(R.string.config_rounds),
        value = mission.rounds,
        tag = "mission_config_rounds",
        onChange = { onChange(mission.copy(rounds = it)) },
        step = 1,
        min = 1,
        max = MAX_ROUNDS,
    )
    Text(text = stringResource(R.string.config_volume), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WordBeatVolumeLevel.entries.forEach { level ->
            FilterChip(
                selected = mission.volumeLevel == level,
                onClick = { onChange(mission.copy(volumeLevel = level)) },
                label = { Text(stringResource(level.labelRes())) },
                modifier = Modifier.testTag("mission_config_volume:${level.name}"),
            )
        }
    }
}

private fun Mission.Typing.isCustom(): Boolean = typingSentences.any {
    it.type == TypingSentenceType.CUSTOM
}

private fun Mission.withTimeLimit(seconds: Int): Mission =
    when (this) {
        is Mission.Math -> copy(timeLimitSeconds = seconds)
        is Mission.Memory -> copy(timeLimitSeconds = seconds)
        is Mission.Shake -> copy(timeLimitSeconds = seconds)
        is Mission.Step -> copy(timeLimitSeconds = seconds)
        is Mission.Squat -> copy(timeLimitSeconds = seconds)
        is Mission.Typing -> copy(timeLimitSeconds = seconds)
        is Mission.Barcode -> copy(timeLimitSeconds = seconds)
        is Mission.Photo -> copy(timeLimitSeconds = seconds)
        is Mission.ImageRecognition -> copy(timeLimitSeconds = seconds)
        is Mission.WordBeat -> copy(timeLimitSeconds = seconds)
    }

internal fun TypingSentenceType.labelRes(): Int =
    when (this) {
        TypingSentenceType.SHORT -> R.string.sentence_short
        TypingSentenceType.MOTIVATIONAL -> R.string.sentence_motivational
        TypingSentenceType.AFFIRMATION -> R.string.sentence_affirmation
        TypingSentenceType.LOVE -> R.string.sentence_love
        TypingSentenceType.STUDY -> R.string.sentence_study
        TypingSentenceType.DIFFICULT -> R.string.sentence_difficult
        TypingSentenceType.CUSTOM -> R.string.sentence_custom
    }

internal fun WordBeatVolumeLevel.labelRes(): Int =
    when (this) {
        WordBeatVolumeLevel.LOW -> R.string.volume_low
        WordBeatVolumeLevel.MEDIUM -> R.string.volume_medium
        WordBeatVolumeLevel.HIGH -> R.string.volume_high
    }

/** Human-readable object name derived from the enum name (no per-object strings to translate). */
internal fun ImageRecognitionObject.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private const val MAX_ROUNDS = 50
private const val MAX_COUNT = 10_000
private const val MAX_TIME_LIMIT = 3600
