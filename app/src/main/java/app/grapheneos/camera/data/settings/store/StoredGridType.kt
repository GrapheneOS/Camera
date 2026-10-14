package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StoredGridTypeSerializer::class)
internal enum class StoredGridType {
    UNKNOWN,
    NONE,
    THREE_BY_THREE,
    FOUR_BY_FOUR,
    GOLDEN_RATIO,
}

internal object StoredGridTypeSerializer : KSerializer<StoredGridType> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "StoredGridType",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: StoredGridType) {
        encoder.encodeString(wireName(value))
    }

    override fun deserialize(decoder: Decoder): StoredGridType {
        val stored = decoder.decodeString()

        return StoredGridType.entries.firstOrNull { wireName(it) == stored }
            ?: StoredGridType.UNKNOWN
    }

    private fun wireName(type: StoredGridType): String {
        return when (type) {
            StoredGridType.UNKNOWN -> "UNKNOWN"
            StoredGridType.NONE -> "NONE"
            StoredGridType.THREE_BY_THREE -> "THREE_BY_THREE"
            StoredGridType.FOUR_BY_FOUR -> "FOUR_BY_FOUR"
            StoredGridType.GOLDEN_RATIO -> "GOLDEN_RATIO"
        }
    }
}
