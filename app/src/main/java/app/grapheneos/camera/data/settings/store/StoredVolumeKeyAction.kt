package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StoredVolumeKeyActionSerializer::class)
internal enum class StoredVolumeKeyAction {
    UNKNOWN,
    SHUTTER,
    ZOOM,
    VOLUME,
    OFF,
}

internal object StoredVolumeKeyActionSerializer : KSerializer<StoredVolumeKeyAction> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "StoredVolumeKeyAction",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: StoredVolumeKeyAction) {
        encoder.encodeString(wireName(value))
    }

    override fun deserialize(decoder: Decoder): StoredVolumeKeyAction {
        val stored = decoder.decodeString()

        return StoredVolumeKeyAction.entries.firstOrNull { wireName(it) == stored }
            ?: StoredVolumeKeyAction.UNKNOWN
    }

    private fun wireName(action: StoredVolumeKeyAction): String {
        return when (action) {
            StoredVolumeKeyAction.UNKNOWN -> "UNKNOWN"
            StoredVolumeKeyAction.SHUTTER -> "SHUTTER"
            StoredVolumeKeyAction.ZOOM -> "ZOOM"
            StoredVolumeKeyAction.VOLUME -> "VOLUME"
            StoredVolumeKeyAction.OFF -> "OFF"
        }
    }
}
