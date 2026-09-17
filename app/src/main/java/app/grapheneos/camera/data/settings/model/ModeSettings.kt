package app.grapheneos.camera.data.settings.model

import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality

data class ModeSettings(
    val flashMode: FlashMode = SettingsDefaults.FLASH_MODE,
    val geoTagging: Boolean = SettingsDefaults.GEO_TAGGING,
    val selfIllumination: Boolean = SettingsDefaults.SELF_ILLUMINATION,
    val videoQuality: VideoQuality = SettingsDefaults.VIDEO_QUALITY,
)
