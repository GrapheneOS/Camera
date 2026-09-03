package app.grapheneos.camera.data.settings.model

import androidx.camera.video.Quality

data class ModeSettings(
    val flashMode: Int = SettingsDefaults.FLASH_MODE,
    val geoTagging: Boolean = SettingsDefaults.GEO_TAGGING,
    val selfIllumination: Boolean = SettingsDefaults.SELF_ILLUMINATION,
    val videoQuality: Quality = SettingsDefaults.VIDEO_QUALITY,
)
