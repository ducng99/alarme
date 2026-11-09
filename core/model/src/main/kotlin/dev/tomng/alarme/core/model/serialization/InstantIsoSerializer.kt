package dev.tomng.alarme.core.model.serialization

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serialises an [Instant] as an ISO-8601 string so exported files stay readable and stable across
 * timezones (epoch millis are equally stable but unreadable in a hand-inspected backup).
 */
object InstantIsoSerializer : KSerializer<Instant> {

    override val descriptor =
        PrimitiveSerialDescriptor("dev.tomng.alarme.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
