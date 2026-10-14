package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StoredVideoQualitySerializer::class)
internal enum class StoredVideoQuality {
    DEVICE_CHOICE,
    UHD,
    FHD,
    HD,
    SD,
}

// Unknown enum names must not make DataStore replace the entire settings file as corrupt.
internal object StoredVideoQualitySerializer : KSerializer<StoredVideoQuality> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "StoredVideoQuality",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: StoredVideoQuality) {
        encoder.encodeString(wireName(value))
    }

    override fun deserialize(decoder: Decoder): StoredVideoQuality {
        val stored = decoder.decodeString()

        return StoredVideoQuality.entries.firstOrNull { wireName(it) == stored }
            ?: StoredVideoQuality.DEVICE_CHOICE
    }

    // Enum constant names are not the persisted wire format.
    private fun wireName(quality: StoredVideoQuality): String {
        return when (quality) {
            StoredVideoQuality.DEVICE_CHOICE -> "DEVICE_CHOICE"
            StoredVideoQuality.UHD -> "UHD"
            StoredVideoQuality.FHD -> "FHD"
            StoredVideoQuality.HD -> "HD"
            StoredVideoQuality.SD -> "SD"
        }
    }
}
