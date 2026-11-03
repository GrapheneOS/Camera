package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.StoredFlashMode
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import javax.inject.Inject

internal interface ModeSettingsMapper {

    fun map(stored: StoredModeSettings, lensFacing: LensFacing): ModeSettings

    fun map(flashMode: FlashMode): StoredFlashMode
}

internal class ModeSettingsMapperImpl @Inject constructor() : ModeSettingsMapper {

    override fun map(
        stored: StoredModeSettings,
        lensFacing: LensFacing,
    ): ModeSettings {
        return ModeSettings(
            flashMode = mapFlashMode(stored.flashMode),
            selfIllumination = stored.selfIllumination ?: SettingsDefaults.SELF_ILLUMINATION,
            videoQuality = mapVideoQuality(stored.videoQuality(lensFacing)),
            videoFrameRate = mapVideoFrameRate(stored.frameRate(lensFacing)),
        )
    }

    override fun map(flashMode: FlashMode): StoredFlashMode {
        return when (flashMode) {
            FlashMode.AUTO -> StoredFlashMode.AUTO
            FlashMode.ON -> StoredFlashMode.ON
            FlashMode.OFF -> StoredFlashMode.OFF
            FlashMode.LOW_LIGHT_BOOST -> StoredFlashMode.LOW_LIGHT_BOOST
        }
    }

    private fun mapFlashMode(stored: StoredFlashMode?): FlashMode {
        return when (stored) {
            StoredFlashMode.AUTO -> FlashMode.AUTO
            StoredFlashMode.ON -> FlashMode.ON
            StoredFlashMode.OFF -> FlashMode.OFF
            StoredFlashMode.LOW_LIGHT_BOOST -> FlashMode.LOW_LIGHT_BOOST
            StoredFlashMode.UNKNOWN -> SettingsDefaults.FLASH_MODE
            null -> SettingsDefaults.FLASH_MODE
        }
    }

    private fun mapVideoFrameRate(framesPerSecond: Int?): VideoFrameRate {
        return VideoFrameRate.entries.firstOrNull { it.framesPerSecond == framesPerSecond }
            ?: SettingsDefaults.VIDEO_FRAME_RATE
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
