package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredModeSettings(
    @SerialName("flash_mode_front")
    val flashModeFront: StoredFlashMode? = null,
    @SerialName("flash_mode_back")
    val flashModeBack: StoredFlashMode? = null,
    @SerialName("video_quality_front")
    val videoQualityFront: StoredVideoQuality = StoredVideoQuality.DEVICE_CHOICE,
    @SerialName("video_quality_back")
    val videoQualityBack: StoredVideoQuality = StoredVideoQuality.DEVICE_CHOICE,
    @SerialName("frame_rate_front")
    val frameRateFront: Int? = null,
    @SerialName("frame_rate_back")
    val frameRateBack: Int? = null,
)
