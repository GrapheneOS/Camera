package app.grapheneos.camera.data.settings.store

import app.grapheneos.camera.data.core.model.LensFacing
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
    @SerialName("frame_rate_front")
    val frameRateFront: Int? = null,
    @SerialName("frame_rate_back")
    val frameRateBack: Int? = null,
) {

    fun videoQuality(lensFacing: LensFacing): StoredVideoQuality {
        return when (lensFacing) {
            LensFacing.FRONT -> videoQualityFront
            LensFacing.BACK -> videoQualityBack
        }
    }

    fun frameRate(lensFacing: LensFacing): Int? {
        return when (lensFacing) {
            LensFacing.FRONT -> frameRateFront
            LensFacing.BACK -> frameRateBack
        }
    }

    fun withVideoQuality(
        lensFacing: LensFacing,
        quality: StoredVideoQuality,
    ): StoredModeSettings {
        return when (lensFacing) {
            LensFacing.FRONT -> copy(videoQualityFront = quality)
            LensFacing.BACK -> copy(videoQualityBack = quality)
        }
    }

    fun withFrameRate(
        lensFacing: LensFacing,
        framesPerSecond: Int,
    ): StoredModeSettings {
        return when (lensFacing) {
            LensFacing.FRONT -> copy(frameRateFront = framesPerSecond)
            LensFacing.BACK -> copy(frameRateBack = framesPerSecond)
        }
    }
}
