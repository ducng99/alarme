package dev.tomng.alarme.feature.backup

/**
 * Why a backup operation failed. The UI maps these to string resources, so the messages stay
 * translatable and the tests can assert on the reason instead of on prose.
 */
enum class BackupError {
    /** The file is not a ZIP / not readable as one. */
    NOT_AN_ARCHIVE,

    /** The archive has no `alarms.json`. */
    MISSING_DATA,

    /** The JSON is malformed or violates domain invariants. */
    INVALID_DATA,

    /** Written by a newer app version. */
    NEWER_VERSION,

    /** More alarms than any real list would have. */
    TOO_MANY_ALARMS,

    /** An entry (or the audio total) exceeds the size caps. */
    TOO_LARGE,

    /** A sound listed in the manifest is absent from the archive. */
    MISSING_AUDIO,

    /** The destination/source document could not be opened. */
    CANNOT_OPEN,

    /** Writing a restored sound file failed. */
    CANNOT_WRITE,
}

/** Anything that makes a file unusable as a backup, carrying a translatable [error]. */
class BackupFormatException(val error: BackupError, cause: Throwable? = null) :
    Exception(error.name, cause)
