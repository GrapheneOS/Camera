package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.data.settings.store.StoredAspectRatio
import app.grapheneos.camera.data.settings.store.StoredCameraSettings
import app.grapheneos.camera.data.settings.store.StoredCaptureMode
import app.grapheneos.camera.data.settings.store.StoredGridType
import app.grapheneos.camera.data.settings.store.StoredVolumeKeyAction
import javax.inject.Inject

internal interface CameraSettingsMapper {

    fun map(stored: StoredCameraSettings): CameraSettings

    fun map(settings: CameraSettings): StoredCameraSettings
}

internal class CameraSettingsMapperImpl @Inject constructor() : CameraSettingsMapper {

    @Suppress("CyclomaticComplexMethod")
    override fun map(stored: StoredCameraSettings): CameraSettings {
        return CameraSettings(
            aspectRatio = map(stored.aspectRatio),
            gridType = map(stored.gridType),
            focusTimeoutSeconds = stored.focusTimeoutSeconds
                ?: SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
            volumeKeyAction = map(stored.volumeKeyAction),
            selfTimerDurationSeconds = stored.selfTimerDurationSeconds
                ?: SettingsDefaults.SELF_TIMER_DURATION,
            timerLight = stored.timerLight ?: SettingsDefaults.TIMER_LIGHT,
            enableCameraSounds = stored.enableCameraSounds ?: SettingsDefaults.CAMERA_SOUNDS,
            includeAudio = stored.includeAudio ?: SettingsDefaults.INCLUDE_AUDIO,
            enableEis = stored.enableEis ?: SettingsDefaults.ENABLE_EIS,
            hdrVideo = stored.hdrVideo ?: SettingsDefaults.HDR_VIDEO,
            captureMode = captureMode(stored),
            selectHighestResolution = stored.selectHighestResolution
                ?: SettingsDefaults.SELECT_HIGHEST_RESOLUTION,
            photoQuality = stored.photoQuality ?: SettingsDefaults.PHOTO_QUALITY,
            geoTagging = stored.geoTagging ?: SettingsDefaults.GEO_TAGGING,
            removeExifAfterCapture = stored.removeExifAfterCapture
                ?: SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
            gyroscopeSuggestions = stored.gyroscopeSuggestions
                ?: SettingsDefaults.GYROSCOPE_SUGGESTIONS,
            keepSelfiesAsSeen = stored.keepSelfiesAsSeen
                ?: stored.saveImageAsPreviewed
                ?: SettingsDefaults.KEEP_SELFIES_AS_SEEN,
            scanAllCodes = stored.scanAllCodes ?: SettingsDefaults.SCAN_ALL_CODES,
            enabledBarcodeFormats = stored.enabledBarcodeFormats
                ?: SettingsDefaults.ENABLED_BARCODE_FORMATS,
        )
    }

    @Suppress("SimplifyBooleanWithConstants")
    override fun map(settings: CameraSettings): StoredCameraSettings {
        return StoredCameraSettings(
            aspectRatio = settings.aspectRatio
                .takeUnless { it == SettingsDefaults.ASPECT_RATIO }
                ?.let(::map),
            gridType = settings.gridType
                .takeUnless { it == SettingsDefaults.GRID_TYPE }
                ?.let(::map),
            focusTimeoutSeconds = settings.focusTimeoutSeconds
                .takeUnless { it == SettingsDefaults.FOCUS_TIMEOUT_SECONDS },
            volumeKeyAction = settings.volumeKeyAction
                .takeUnless { it == SettingsDefaults.VOLUME_KEY_ACTION }
                ?.let(::map),
            selfTimerDurationSeconds = settings.selfTimerDurationSeconds
                .takeUnless { it == SettingsDefaults.SELF_TIMER_DURATION },
            timerLight = settings.timerLight
                .takeUnless { it == SettingsDefaults.TIMER_LIGHT },
            enableCameraSounds = settings.enableCameraSounds
                .takeUnless { it == SettingsDefaults.CAMERA_SOUNDS },
            includeAudio = settings.includeAudio
                .takeUnless { it == SettingsDefaults.INCLUDE_AUDIO },
            enableEis = settings.enableEis
                .takeUnless { it == SettingsDefaults.ENABLE_EIS },
            hdrVideo = settings.hdrVideo
                .takeUnless { it == SettingsDefaults.HDR_VIDEO },
            captureMode = settings.captureMode
                .takeUnless { it == SettingsDefaults.CAPTURE_MODE }
                ?.let(::map),
            selectHighestResolution = settings.selectHighestResolution
                .takeUnless { it == SettingsDefaults.SELECT_HIGHEST_RESOLUTION },
            photoQuality = settings.photoQuality
                .takeUnless { it == SettingsDefaults.PHOTO_QUALITY },
            geoTagging = settings.geoTagging
                .takeUnless { it == SettingsDefaults.GEO_TAGGING },
            removeExifAfterCapture = settings.removeExifAfterCapture
                .takeUnless { it == SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE },
            gyroscopeSuggestions = settings.gyroscopeSuggestions
                .takeUnless { it == SettingsDefaults.GYROSCOPE_SUGGESTIONS },
            keepSelfiesAsSeen = settings.keepSelfiesAsSeen
                .takeUnless { it == SettingsDefaults.KEEP_SELFIES_AS_SEEN },
            scanAllCodes = settings.scanAllCodes
                .takeUnless { it == SettingsDefaults.SCAN_ALL_CODES },
            enabledBarcodeFormats = settings.enabledBarcodeFormats
                .takeUnless { it == SettingsDefaults.ENABLED_BARCODE_FORMATS },
        )
    }

    private fun captureMode(stored: StoredCameraSettings): CaptureMode {
        return when (stored.captureMode) {
            StoredCaptureMode.SPEED -> CaptureMode.SPEED
            StoredCaptureMode.QUALITY -> CaptureMode.QUALITY
            StoredCaptureMode.ZERO_SHUTTER_LAG -> CaptureMode.ZERO_SHUTTER_LAG
            StoredCaptureMode.UNKNOWN -> SettingsDefaults.CAPTURE_MODE
            null -> legacyCaptureMode(stored)
        }
    }

    // Before capture_mode, the bind let waiting for focus win over zero shutter lag.
    private fun legacyCaptureMode(stored: StoredCameraSettings): CaptureMode {
        return when {
            stored.waitForFocusLock == true -> CaptureMode.QUALITY
            stored.enableZsl == true -> CaptureMode.ZERO_SHUTTER_LAG
            else -> SettingsDefaults.CAPTURE_MODE
        }
    }

    private fun map(mode: CaptureMode): StoredCaptureMode {
        return when (mode) {
            CaptureMode.SPEED -> StoredCaptureMode.SPEED
            CaptureMode.QUALITY -> StoredCaptureMode.QUALITY
            CaptureMode.ZERO_SHUTTER_LAG -> StoredCaptureMode.ZERO_SHUTTER_LAG
        }
    }

    private fun map(stored: StoredAspectRatio?): AspectRatio {
        return when (stored) {
            StoredAspectRatio.RATIO_4_3 -> AspectRatio.RATIO_4_3
            StoredAspectRatio.RATIO_16_9 -> AspectRatio.RATIO_16_9

            StoredAspectRatio.UNKNOWN,
            null,
            -> SettingsDefaults.ASPECT_RATIO
        }
    }

    private fun map(aspectRatio: AspectRatio): StoredAspectRatio {
        return when (aspectRatio) {
            AspectRatio.RATIO_4_3 -> StoredAspectRatio.RATIO_4_3
            AspectRatio.RATIO_16_9 -> StoredAspectRatio.RATIO_16_9
        }
    }

    private fun map(stored: StoredGridType?): GridType {
        return when (stored) {
            StoredGridType.NONE -> GridType.NONE
            StoredGridType.THREE_BY_THREE -> GridType.THREE_BY_THREE
            StoredGridType.FOUR_BY_FOUR -> GridType.FOUR_BY_FOUR
            StoredGridType.GOLDEN_RATIO -> GridType.GOLDEN_RATIO

            StoredGridType.UNKNOWN,
            null,
            -> SettingsDefaults.GRID_TYPE
        }
    }

    private fun map(type: GridType): StoredGridType {
        return when (type) {
            GridType.NONE -> StoredGridType.NONE
            GridType.THREE_BY_THREE -> StoredGridType.THREE_BY_THREE
            GridType.FOUR_BY_FOUR -> StoredGridType.FOUR_BY_FOUR
            GridType.GOLDEN_RATIO -> StoredGridType.GOLDEN_RATIO
        }
    }

    private fun map(stored: StoredVolumeKeyAction?): VolumeKeyAction {
        return when (stored) {
            StoredVolumeKeyAction.SHUTTER -> VolumeKeyAction.SHUTTER
            StoredVolumeKeyAction.ZOOM -> VolumeKeyAction.ZOOM
            StoredVolumeKeyAction.VOLUME -> VolumeKeyAction.VOLUME
            StoredVolumeKeyAction.OFF -> VolumeKeyAction.OFF
            StoredVolumeKeyAction.UNKNOWN -> SettingsDefaults.VOLUME_KEY_ACTION
            null -> SettingsDefaults.VOLUME_KEY_ACTION
        }
    }

    private fun map(action: VolumeKeyAction): StoredVolumeKeyAction {
        return when (action) {
            VolumeKeyAction.SHUTTER -> StoredVolumeKeyAction.SHUTTER
            VolumeKeyAction.ZOOM -> StoredVolumeKeyAction.ZOOM
            VolumeKeyAction.VOLUME -> StoredVolumeKeyAction.VOLUME
            VolumeKeyAction.OFF -> StoredVolumeKeyAction.OFF
        }
    }
}
