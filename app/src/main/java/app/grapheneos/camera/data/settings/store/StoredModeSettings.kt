package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredModeSettings(
    @SerialName("flash_mode")
    val flashMode: StoredFlashMode? = null,
    @SerialName("self_illumination")
    val selfIllumination: Boolean? = null,
    @SerialName("video_quality_front")
    val videoQualityFront: StoredVideoQuality = StoredVideoQuality.DEVICE_CHOICE,
    @SerialName("video_quality_back")
    val videoQualityBack: StoredVideoQuality = StoredVideoQuality.DEVICE_CHOICE,
)
