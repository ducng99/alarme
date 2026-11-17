package dev.tomng.alarme.core.model.mission

import kotlinx.serialization.Serializable

/** Persistence/API vocabulary for mission types (matches the flattened storage rows). */
@Serializable
enum class MissionType {
    MATH,
    MEMORY,
    SHAKE,
    STEP,
    SQUAT,
    TYPING,
    BARCODE,
    PHOTO,
    WORD_BEAT,
}
