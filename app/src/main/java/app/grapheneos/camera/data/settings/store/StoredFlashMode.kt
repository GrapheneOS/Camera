package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StoredFlashModeSerializer::class)
internal enum class StoredFlashMode {
    UNKNOWN,
    AUTO,
    ON,
    OFF,
}

internal object StoredFlashModeSerializer : KSerializer<StoredFlashMode> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "StoredFlashMode",
        kind = PrimitiveKind.INT,
    )

    override fun serialize(encoder: Encoder, value: StoredFlashMode) {
        encoder.encodeInt(wireValue(value))
    }

    override fun deserialize(decoder: Decoder): StoredFlashMode {
        return fromWireValue(decoder.decodeInt())
    }

    fun fromWireValue(stored: Int): StoredFlashMode {
        return StoredFlashMode.entries.firstOrNull { wireValue(it) == stored }
            ?: StoredFlashMode.UNKNOWN
    }

    // The stored values are CameraX's ImageCapture.FLASH_MODE_* constants.
    private fun wireValue(flashMode: StoredFlashMode): Int {
        return when (flashMode) {
            StoredFlashMode.UNKNOWN -> UNKNOWN_WIRE_VALUE
            StoredFlashMode.AUTO -> 0
            StoredFlashMode.ON -> 1
            StoredFlashMode.OFF -> 2
        }
    }

    private const val UNKNOWN_WIRE_VALUE = -1
}
