package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StoredCaptureModeSerializer::class)
internal enum class StoredCaptureMode {
    UNKNOWN,
    SPEED,
    QUALITY,
    ZERO_SHUTTER_LAG,
}

internal object StoredCaptureModeSerializer : KSerializer<StoredCaptureMode> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "StoredCaptureMode",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: StoredCaptureMode) {
        encoder.encodeString(wireName(value))
    }

    override fun deserialize(decoder: Decoder): StoredCaptureMode {
        val stored = decoder.decodeString()

        return StoredCaptureMode.entries.firstOrNull { wireName(it) == stored }
            ?: StoredCaptureMode.UNKNOWN
    }

    private fun wireName(mode: StoredCaptureMode): String {
        return when (mode) {
            StoredCaptureMode.UNKNOWN -> "UNKNOWN"
            StoredCaptureMode.SPEED -> "SPEED"
            StoredCaptureMode.QUALITY -> "QUALITY"
            StoredCaptureMode.ZERO_SHUTTER_LAG -> "ZERO_SHUTTER_LAG"
        }
    }
}
