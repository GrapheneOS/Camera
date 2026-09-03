package app.grapheneos.camera.data.settings.store

import androidx.datastore.core.Serializer
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.JsonPreferenceSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
internal data class SettingsPrefs(
    val common: StoredCameraSettings = StoredCameraSettings(),
    val modes: Map<String, StoredModeSettings> = emptyMap(),
) {
    fun mode(mode: CameraMode): StoredModeSettings {
        return modes[storedModeName(mode)] ?: StoredModeSettings()
    }

    fun withMode(mode: CameraMode, settings: StoredModeSettings): SettingsPrefs {
        return copy(modes = modes + (storedModeName(mode) to settings))
    }

    companion object {
        fun storedModeName(mode: CameraMode): String {
            return when (mode) {
                CameraMode.QR_SCAN -> "QR_SCAN"
                CameraMode.AUTO -> "AUTO"
                CameraMode.FACE_RETOUCH -> "FACE_RETOUCH"
                CameraMode.PORTRAIT -> "PORTRAIT"
                CameraMode.NIGHT -> "NIGHT"
                CameraMode.HDR -> "HDR"
                CameraMode.CAMERA -> "CAMERA"
                CameraMode.VIDEO -> "VIDEO"
            }
        }
    }
}

@Serializable
internal data class StoredCameraSettings(
    @SerialName("aspect_ratio")
    val aspectRatio: Int? = null,
    @SerialName("grid_type")
    val gridType: StoredGridType? = null,
    @SerialName("focus_timeout_seconds")
    val focusTimeoutSeconds: Long? = null,
    @SerialName("self_timer_duration_seconds")
    val selfTimerDurationSeconds: Int? = null,
    @SerialName("enable_camera_sounds")
    val enableCameraSounds: Boolean? = null,
    @SerialName("include_audio")
    val includeAudio: Boolean? = null,
    @SerialName("enable_eis")
    val enableEis: Boolean? = null,
    @SerialName("enable_zsl")
    val enableZsl: Boolean? = null,
    @SerialName("wait_for_focus_lock")
    val waitForFocusLock: Boolean? = null,
    @SerialName("select_highest_resolution")
    val selectHighestResolution: Boolean? = null,
    @SerialName("photo_quality")
    val photoQuality: Int? = null,
    @SerialName("remove_exif_after_capture")
    val removeExifAfterCapture: Boolean? = null,
    @SerialName("gyroscope_suggestions")
    val gyroscopeSuggestions: Boolean? = null,
    @SerialName("save_image_as_previewed")
    val saveImageAsPreviewed: Boolean? = null,
    @SerialName("save_video_as_previewed")
    val saveVideoAsPreviewed: Boolean? = null,
    @SerialName("scan_all_codes")
    val scanAllCodes: Boolean? = null,
    @SerialName("enabled_barcode_formats")
    val enabledBarcodeFormats: Set<String>? = null,
)

@Serializable
internal data class StoredModeSettings(
    @SerialName("flash_mode")
    val flashMode: Int? = null,
    @SerialName("geo_tagging")
    val geoTagging: Boolean? = null,
    @SerialName("self_illumination")
    val selfIllumination: Boolean? = null,
    @SerialName("video_quality_front")
    val videoQualityFront: StoredVideoQuality = StoredVideoQuality.DEVICE_CHOICE,
    @SerialName("video_quality_back")
    val videoQualityBack: StoredVideoQuality = StoredVideoQuality.DEVICE_CHOICE,
)

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

internal val settingsPrefsSerializer: Serializer<SettingsPrefs> = JsonPreferenceSerializer(
    serializer = SettingsPrefs.serializer(),
    defaultValue = SettingsPrefs(),
)
