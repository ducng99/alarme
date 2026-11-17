package dev.tomng.alarme.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.model.mission.WordBeatVolumeLevel
import dev.tomng.alarme.feature.missions.ui.BarcodeRegistrationDialog
import kotlin.math.roundToInt

@Composable
internal fun MissionFields(mission: Mission, onChange: (Mission) -> Unit) {
    when (mission) {
        is Mission.Math -> {
            RoundsSection(mission.rounds) { onChange(mission.copy(rounds = it)) }
            DifficultySection(
                range = Mission.Math.MIN_DIFFICULTY..Mission.Math.MAX_DIFFICULTY,
                selected = mission.difficulty,
                description = mathLevelDescription(mission.difficulty),
                onSelect = { onChange(mission.copy(difficulty = it)) },
            )
        }
        is Mission.Memory -> {
            RoundsSection(mission.rounds) { onChange(mission.copy(rounds = it)) }
            DifficultySection(
                range = Mission.Memory.MIN_DIFFICULTY..Mission.Memory.MAX_DIFFICULTY,
                selected = mission.difficulty,
                description = stringResource(R.string.config_memory_grid, mission.difficulty),
                onSelect = { onChange(mission.copy(difficulty = it)) },
            )
        }
        is Mission.Shake -> CountSection(mission.count) { onChange(mission.copy(count = it)) }
        is Mission.Step -> CountSection(mission.count) { onChange(mission.copy(count = it)) }
        is Mission.Squat -> CountSection(mission.count) { onChange(mission.copy(count = it)) }
        is Mission.Typing -> TypingFields(mission, onChange)
        is Mission.Barcode ->
            ConfigSection(
                title = stringResource(R.string.config_barcode),
                body = stringResource(R.string.config_barcode_hint),
            ) {
                OutlinedTextField(
                    value = mission.barcodeValue,
                    onValueChange = { onChange(mission.copy(barcodeValue = it)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mission_config_barcode"),
                )
                var scanning by remember { mutableStateOf(false) }
                Button(
                    onClick = { scanning = true },
                    modifier = Modifier.testTag("mission_config_barcode_scan"),
                ) {
                    Text(stringResource(R.string.config_barcode_scan))
                }
                if (scanning) {
                    BarcodeRegistrationDialog(
                        onScanned = {
                            scanning = false
                            onChange(mission.copy(barcodeValue = it))
                        },
                        onDismiss = { scanning = false },
                    )
                }
            }
        is Mission.Photo ->
            ConfigSection(
                title = stringResource(R.string.config_photo),
                body = stringResource(R.string.config_photo_body),
            ) {
                OutlinedTextField(
                    value = mission.photoPath,
                    onValueChange = { onChange(mission.copy(photoPath = it)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mission_config_photo"),
                )
            }
        is Mission.ImageRecognition -> ObjectsSection(mission, onChange)
        is Mission.WordBeat -> WordBeatFields(mission, onChange)
    }
}

@Composable
private fun RoundsSection(rounds: Int, onChange: (Int) -> Unit) {
    StepperSection(
        title = stringResource(R.string.config_rounds),
        body = stringResource(R.string.config_rounds_body),
        value = rounds,
        tag = "mission_config_rounds",
        onChange = onChange,
        step = 1,
        min = 1,
        max = MAX_ROUNDS,
    )
}

@Composable
private fun DifficultySection(
    range: IntRange,
    selected: Int,
    description: String,
    onSelect: (Int) -> Unit,
) {
    ConfigSection(
        title = stringResource(R.string.config_difficulty),
        body = description,
        trailing = {
            Text(
                text = stringResource(R.string.config_level, selected),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Column {
            Slider(
                value = selected.toFloat(),
                onValueChange = { value ->
                    val level = value.roundToInt()
                    if (level != selected) onSelect(level)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
                modifier = Modifier.fillMaxWidth().testTag("mission_config_difficulty"),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = range.first.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = range.last.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun mathLevelDescription(level: Int): String {
    val mode =
        if (level == 0) stringResource(R.string.config_math_choice)
        else stringResource(R.string.config_math_keypad)
    val example = MATH_EXAMPLES.getOrElse(level) { MATH_EXAMPLES.last() }
    return mode + " · " + stringResource(R.string.config_example, example)
}

@Composable
private fun CountSection(count: Int, onChange: (Int) -> Unit) {
    StepperSection(
        title = stringResource(R.string.config_count),
        value = count,
        tag = "mission_config_count",
        onChange = onChange,
        step = 5,
        min = 1,
        max = MAX_COUNT,
    ) {
        ChoiceChips(
            options = COUNT_PRESETS,
            isSelected = { it == count },
            label = { it.toString() },
            tag = { "mission_config_count_preset:$it" },
            onSelect = onChange,
        )
    }
}

@Composable
private fun TypingFields(mission: Mission.Typing, onChange: (Mission.Typing) -> Unit) {
    var custom by remember { mutableStateOf(mission.isCustom()) }
    var customText by remember {
        mutableStateOf(
            if (mission.isCustom()) mission.typingSentences.joinToString("\n") { it.sentence }
            else "",
        )
    }
    val selectedType =
        if (custom) TypingSentenceType.CUSTOM else mission.typingSentences.firstOrNull()?.type
    ConfigSection(title = stringResource(R.string.config_sentences)) {
        ChoiceChips(
            options = TypingSentenceType.entries,
            isSelected = { it == selectedType },
            label = { stringResource(it.labelRes()) },
            tag = { "mission_config_sentence:${it.name}" },
            onSelect = { type ->
                custom = type == TypingSentenceType.CUSTOM
                customText = ""
                onChange(mission.copy(typingSentences = TypingSentenceCatalog.pack(type)))
            },
        )
        if (custom) {
            OutlinedTextField(
                value = customText,
                onValueChange = { text ->
                    customText = text
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
                placeholder = { Text(stringResource(R.string.config_custom_sentences_hint)) },
                minLines = 3,
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("mission_config_custom_sentences"),
            )
        } else {
            SentencePreview(mission.typingSentences)
        }
    }
    RoundsSection(mission.rounds) { onChange(mission.copy(rounds = it)) }
}

@Composable
private fun SentencePreview(sentences: List<TypingSentence>) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            sentences.forEach { sentence ->
                Text(
                    text = "\u201c${sentence.sentence}\u201d",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ObjectsSection(
    mission: Mission.ImageRecognition,
    onChange: (Mission.ImageRecognition) -> Unit,
) {
    ConfigSection(
        title = stringResource(R.string.config_objects),
        trailing = {
            Text(
                text = stringResource(R.string.config_selected, mission.objects.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        ChoiceChips(
            options = ImageRecognitionObject.entries,
            isSelected = { it in mission.objects },
            label = { it.displayName() },
            tag = { "mission_config_object:${it.name}" },
            onSelect = { obj ->
                val objects =
                    if (obj in mission.objects) mission.objects - obj else mission.objects + obj
                onChange(mission.copy(objects = objects))
            },
        )
    }
}

@Composable
private fun WordBeatFields(mission: Mission.WordBeat, onChange: (Mission.WordBeat) -> Unit) {
    ConfigSection(title = stringResource(R.string.config_language)) {
        SegmentedChoice(
            options = WordBeatLanguage.entries,
            selected = mission.language,
            label = { stringResource(languageLabel(it)) },
            tag = { "mission_config_language:${it.name}" },
            onSelect = { language ->
                onChange(
                    mission.copy(
                        language = language,
                        selectedRoundSetIds = MissionDefaults.roundSetIds(language),
                    ),
                )
            },
        )
    }
    RoundsSection(mission.rounds) { onChange(mission.copy(rounds = it)) }
    ConfigSection(
        title = stringResource(R.string.config_volume),
        body = stringResource(R.string.config_volume_body),
    ) {
        SegmentedChoice(
            options = WordBeatVolumeLevel.entries,
            selected = mission.volumeLevel,
            label = { stringResource(it.labelRes()) },
            tag = { "mission_config_volume:${it.name}" },
            onSelect = { onChange(mission.copy(volumeLevel = it)) },
        )
    }
}

private fun Mission.Typing.isCustom(): Boolean = typingSentences.any {
    it.type == TypingSentenceType.CUSTOM
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

private val MATH_EXAMPLES =
    listOf("3+5", "7+8", "34+58", "12+47+85", "(23×7)+45", "(23×45)+678", "(234×56)+7890")
private val COUNT_PRESETS = listOf(10, 20, 30, 50, 100)
private const val MAX_ROUNDS = 50
private const val MAX_COUNT = 10_000
