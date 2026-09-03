package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.StoredCameraSettings
import app.grapheneos.camera.data.settings.store.StoredGridType
import javax.inject.Inject

internal interface CameraSettingsMapper {

    fun map(stored: StoredCameraSettings): CameraSettings

    fun map(settings: CameraSettings): StoredCameraSettings
}

internal class CameraSettingsMapperImpl @Inject constructor() : CameraSettingsMapper {

    @Suppress("CyclomaticComplexMethod")
    override fun map(stored: StoredCameraSettings): CameraSettings {
        return CameraSettings(
            aspectRatio = stored.aspectRatio ?: SettingsDefaults.ASPECT_RATIO,
            gridType = map(stored.gridType),
            focusTimeoutSeconds = stored.focusTimeoutSeconds
                ?: SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
            selfTimerDurationSeconds = stored.selfTimerDurationSeconds
                ?: SettingsDefaults.SELF_TIMER_DURATION,
            enableCameraSounds = stored.enableCameraSounds ?: SettingsDefaults.CAMERA_SOUNDS,
            includeAudio = stored.includeAudio ?: SettingsDefaults.INCLUDE_AUDIO,
            enableEis = stored.enableEis ?: SettingsDefaults.ENABLE_EIS,
            enableZsl = stored.enableZsl ?: SettingsDefaults.ENABLE_ZSL,
            waitForFocusLock = stored.waitForFocusLock ?: SettingsDefaults.WAIT_FOR_FOCUS_LOCK,
            selectHighestResolution = stored.selectHighestResolution
                ?: SettingsDefaults.SELECT_HIGHEST_RESOLUTION,
            photoQuality = stored.photoQuality ?: SettingsDefaults.PHOTO_QUALITY,
            removeExifAfterCapture = stored.removeExifAfterCapture
                ?: SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
            gyroscopeSuggestions = stored.gyroscopeSuggestions
                ?: SettingsDefaults.GYROSCOPE_SUGGESTIONS,
            saveImageAsPreviewed = stored.saveImageAsPreviewed
                ?: SettingsDefaults.SAVE_IMAGE_AS_PREVIEW,
            saveVideoAsPreviewed = stored.saveVideoAsPreviewed
                ?: SettingsDefaults.SAVE_VIDEO_AS_PREVIEW,
            scanAllCodes = stored.scanAllCodes ?: SettingsDefaults.SCAN_ALL_CODES,
            enabledBarcodeFormats = stored.enabledBarcodeFormats
                ?: SettingsDefaults.ENABLED_BARCODE_FORMATS,
        )
    }

    @Suppress("SimplifyBooleanWithConstants")
    override fun map(settings: CameraSettings): StoredCameraSettings {
        return StoredCameraSettings(
            aspectRatio = settings.aspectRatio.takeUnless { it == SettingsDefaults.ASPECT_RATIO },
            gridType = settings.gridType
                .takeUnless { it == SettingsDefaults.GRID_TYPE }
                ?.let(::map),
            focusTimeoutSeconds = settings.focusTimeoutSeconds
                .takeUnless { it == SettingsDefaults.FOCUS_TIMEOUT_SECONDS },
            selfTimerDurationSeconds = settings.selfTimerDurationSeconds
                .takeUnless { it == SettingsDefaults.SELF_TIMER_DURATION },
            enableCameraSounds = settings.enableCameraSounds
                .takeUnless { it == SettingsDefaults.CAMERA_SOUNDS },
            includeAudio = settings.includeAudio
                .takeUnless { it == SettingsDefaults.INCLUDE_AUDIO },
            enableEis = settings.enableEis.takeUnless { it == SettingsDefaults.ENABLE_EIS },
            enableZsl = settings.enableZsl.takeUnless { it == SettingsDefaults.ENABLE_ZSL },
            waitForFocusLock = settings.waitForFocusLock
                .takeUnless { it == SettingsDefaults.WAIT_FOR_FOCUS_LOCK },
            selectHighestResolution = settings.selectHighestResolution
                .takeUnless { it == SettingsDefaults.SELECT_HIGHEST_RESOLUTION },
            photoQuality = settings.photoQuality
                .takeUnless { it == SettingsDefaults.PHOTO_QUALITY },
            removeExifAfterCapture = settings.removeExifAfterCapture
                .takeUnless { it == SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE },
            gyroscopeSuggestions = settings.gyroscopeSuggestions
                .takeUnless { it == SettingsDefaults.GYROSCOPE_SUGGESTIONS },
            saveImageAsPreviewed = settings.saveImageAsPreviewed
                .takeUnless { it == SettingsDefaults.SAVE_IMAGE_AS_PREVIEW },
            saveVideoAsPreviewed = settings.saveVideoAsPreviewed
                .takeUnless { it == SettingsDefaults.SAVE_VIDEO_AS_PREVIEW },
            scanAllCodes = settings.scanAllCodes
                .takeUnless { it == SettingsDefaults.SCAN_ALL_CODES },
            enabledBarcodeFormats = settings.enabledBarcodeFormats
                .takeUnless { it == SettingsDefaults.ENABLED_BARCODE_FORMATS },
        )
    }

    private fun map(stored: StoredGridType?): GridType {
        return when (stored) {
            null,
            StoredGridType.UNKNOWN,
            -> SettingsDefaults.GRID_TYPE
            StoredGridType.NONE -> GridType.NONE
            StoredGridType.THREE_BY_THREE -> GridType.THREE_BY_THREE
            StoredGridType.FOUR_BY_FOUR -> GridType.FOUR_BY_FOUR
            StoredGridType.GOLDEN_RATIO -> GridType.GOLDEN_RATIO
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
}
