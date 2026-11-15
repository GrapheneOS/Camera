package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.StoredFlashMode
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import javax.inject.Inject

internal interface ModeSettingsMapper {

    fun map(
        stored: StoredModeSettings,
        slot: ModeSlot,
    ): ModeSettings

    /** Returns null for modes that must not be persisted. */
    fun map(
        flashMode: FlashMode,
        mode: CameraMode,
    ): StoredFlashMode?
}

internal class ModeSettingsMapperImpl @Inject constructor() : ModeSettingsMapper {

    override fun map(
        stored: StoredModeSettings,
        slot: ModeSlot,
    ): ModeSettings {
        val storedFlashMode = when {
            slot.isFrontFacing -> stored.flashModeFront
            else -> stored.flashModeBack
        }
        val storedQuality = when {
            slot.isFrontFacing -> stored.videoQualityFront
            else -> stored.videoQualityBack
        }
        val storedFrameRate = when {
            slot.isFrontFacing -> stored.frameRateFront
            else -> stored.frameRateBack
        }

        return ModeSettings(
            flashMode = mapFlashMode(
                stored = storedFlashMode,
                mode = slot.mode,
            ),
            videoQuality = mapVideoQuality(storedQuality),
            videoFrameRate = mapVideoFrameRate(storedFrameRate),
        )
    }

    override fun map(
        flashMode: FlashMode,
        mode: CameraMode,
    ): StoredFlashMode? {
        return when (flashMode) {
            FlashMode.AUTO -> StoredFlashMode.AUTO
            FlashMode.ON if mode.isVideo -> null
            FlashMode.ON -> StoredFlashMode.ON
            FlashMode.OFF -> StoredFlashMode.OFF
            FlashMode.LOW_LIGHT_BOOST -> StoredFlashMode.LOW_LIGHT_BOOST
            FlashMode.SELF_ILLUMINATION -> null
        }
    }

    private fun mapFlashMode(
        stored: StoredFlashMode?,
        mode: CameraMode,
    ): FlashMode {
        return when (stored) {
            StoredFlashMode.AUTO -> FlashMode.AUTO
            StoredFlashMode.ON if mode.isVideo -> FlashMode.OFF
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
