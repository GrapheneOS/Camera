package app.grapheneos.camera.data.settings.model

import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality

data class ModeSettings(
    val flashMode: FlashMode = SettingsDefaults.FLASH_MODE,
    val selfIllumination: Boolean = SettingsDefaults.SELF_ILLUMINATION,
    val videoQuality: VideoQuality = SettingsDefaults.VIDEO_QUALITY,
    val videoFrameRate: VideoFrameRate = SettingsDefaults.VIDEO_FRAME_RATE,
) {

    fun isVideoLightOn(): Boolean {
        return flashMode == FlashMode.ON
    }

    fun selfIlluminates(lensFacing: LensFacing): Boolean {
        return when (lensFacing) {
            LensFacing.FRONT -> selfIllumination
            LensFacing.BACK -> false
        }
    }
}
