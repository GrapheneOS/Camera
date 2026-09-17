package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.StoredFlashMode
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import javax.inject.Inject

internal interface ModeSettingsMapper {

    fun map(stored: StoredModeSettings, isFrontFacing: Boolean): ModeSettings

    fun map(flashMode: FlashMode): StoredFlashMode
}

internal class ModeSettingsMapperImpl @Inject constructor() : ModeSettingsMapper {

    override fun map(stored: StoredModeSettings, isFrontFacing: Boolean): ModeSettings {
        val storedQuality = when {
            isFrontFacing -> stored.videoQualityFront
            else -> stored.videoQualityBack
        }

        return ModeSettings(
            flashMode = mapFlashMode(stored.flashMode),
            geoTagging = stored.geoTagging ?: SettingsDefaults.GEO_TAGGING,
            selfIllumination = stored.selfIllumination ?: SettingsDefaults.SELF_ILLUMINATION,
            videoQuality = mapVideoQuality(storedQuality),
        )
    }

    override fun map(flashMode: FlashMode): StoredFlashMode {
        return when (flashMode) {
            FlashMode.AUTO -> StoredFlashMode.AUTO
            FlashMode.ON -> StoredFlashMode.ON
            FlashMode.OFF -> StoredFlashMode.OFF
        }
    }

    private fun mapFlashMode(stored: StoredFlashMode?): FlashMode {
        return when (stored) {
            StoredFlashMode.AUTO -> FlashMode.AUTO
            StoredFlashMode.ON -> FlashMode.ON
            StoredFlashMode.OFF -> FlashMode.OFF

            StoredFlashMode.UNKNOWN,
            null,
            -> SettingsDefaults.FLASH_MODE
        }
    }

    private fun mapVideoQuality(stored: StoredVideoQuality): VideoQuality {
        return when (stored) {
            StoredVideoQuality.DEVICE_CHOICE -> SettingsDefaults.VIDEO_QUALITY
            StoredVideoQuality.UHD -> VideoQuality.UHD
            StoredVideoQuality.FHD -> VideoQuality.FHD
            StoredVideoQuality.HD -> VideoQuality.HD
            StoredVideoQuality.SD -> VideoQuality.SD
        }
    }
}
