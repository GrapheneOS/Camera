package app.grapheneos.camera.data.settings.mapper

import androidx.camera.video.Quality
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import javax.inject.Inject

internal interface ModeSettingsMapper {

    fun map(stored: StoredModeSettings, isFrontFacing: Boolean): ModeSettings
}

internal class ModeSettingsMapperImpl @Inject constructor() : ModeSettingsMapper {

    override fun map(stored: StoredModeSettings, isFrontFacing: Boolean): ModeSettings {
        val storedQuality = when {
            isFrontFacing -> stored.videoQualityFront
            else -> stored.videoQualityBack
        }

        return ModeSettings(
            flashMode = stored.flashMode ?: SettingsDefaults.FLASH_MODE,
            geoTagging = stored.geoTagging ?: SettingsDefaults.GEO_TAGGING,
            selfIllumination = stored.selfIllumination ?: SettingsDefaults.SELF_ILLUMINATION,
            videoQuality = mapVideoQuality(storedQuality),
        )
    }

    private fun mapVideoQuality(stored: StoredVideoQuality): Quality {
        return when (stored) {
            StoredVideoQuality.DEVICE_CHOICE -> SettingsDefaults.VIDEO_QUALITY
            StoredVideoQuality.UHD -> Quality.UHD
            StoredVideoQuality.FHD -> Quality.FHD
            StoredVideoQuality.HD -> Quality.HD
            StoredVideoQuality.SD -> Quality.SD
        }
    }
}
