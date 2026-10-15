package app.grapheneos.camera.data.settings.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CaptureMode

data class CameraSettings(
    val aspectRatio: AspectRatio = SettingsDefaults.ASPECT_RATIO,
    val gridType: GridType = SettingsDefaults.GRID_TYPE,
    val focusTimeoutSeconds: Long = SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
    val volumeKeyAction: VolumeKeyAction = SettingsDefaults.VOLUME_KEY_ACTION,
    val selfTimerDurationSeconds: Int = SettingsDefaults.SELF_TIMER_DURATION,
    val timerLight: Boolean = SettingsDefaults.TIMER_LIGHT,
    val enableCameraSounds: Boolean = SettingsDefaults.CAMERA_SOUNDS,
    val includeAudio: Boolean = SettingsDefaults.INCLUDE_AUDIO,
    val enableEis: Boolean = SettingsDefaults.ENABLE_EIS,
    val hdrVideo: Boolean = SettingsDefaults.HDR_VIDEO,
    val captureMode: CaptureMode = SettingsDefaults.CAPTURE_MODE,
    val selectHighestResolution: Boolean = SettingsDefaults.SELECT_HIGHEST_RESOLUTION,
    val ultraHdr: Boolean = SettingsDefaults.ULTRA_HDR,
    val photoQuality: Int = SettingsDefaults.PHOTO_QUALITY,
    val geoTagging: Boolean = SettingsDefaults.GEO_TAGGING,
    val removeExifAfterCapture: Boolean = SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
    val gyroscopeSuggestions: Boolean = SettingsDefaults.GYROSCOPE_SUGGESTIONS,
    val keepSelfiesAsSeen: Boolean = SettingsDefaults.KEEP_SELFIES_AS_SEEN,
    val scanAllCodes: Boolean = SettingsDefaults.SCAN_ALL_CODES,
    val enabledBarcodeFormats: Set<String> = SettingsDefaults.ENABLED_BARCODE_FORMATS,
) {
    fun withBarcodeFormat(formatName: String, enabled: Boolean): CameraSettings {
        val formats = when {
            enabled -> enabledBarcodeFormats + formatName
            else -> enabledBarcodeFormats - formatName
        }

        return copy(enabledBarcodeFormats = formats)
    }

    companion object {
        val PHOTO_QUALITY_RANGE = 1..100
    }
}
