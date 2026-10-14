package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StoredAspectRatioSerializer::class)
internal enum class StoredAspectRatio {
    UNKNOWN,
    RATIO_4_3,
    RATIO_16_9,
}

internal object StoredAspectRatioSerializer : KSerializer<StoredAspectRatio> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "StoredAspectRatio",
        kind = PrimitiveKind.INT,
    )

    override fun serialize(encoder: Encoder, value: StoredAspectRatio) {
        encoder.encodeInt(wireValue(value))
    }

    override fun deserialize(decoder: Decoder): StoredAspectRatio {
        return fromWireValue(decoder.decodeInt())
    }

    fun fromWireValue(stored: Int): StoredAspectRatio {
        return StoredAspectRatio.entries.firstOrNull { wireValue(it) == stored }
            ?: StoredAspectRatio.UNKNOWN
    }

    // The stored values are CameraX's AspectRatio.RATIO_* constants.
    private fun wireValue(aspectRatio: StoredAspectRatio): Int {
        return when (aspectRatio) {
            StoredAspectRatio.UNKNOWN -> UNKNOWN_WIRE_VALUE
            StoredAspectRatio.RATIO_4_3 -> 0
            StoredAspectRatio.RATIO_16_9 -> 1
        }
    }

    private const val UNKNOWN_WIRE_VALUE = -1
}
