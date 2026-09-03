package app.grapheneos.camera.data.settings.model

data class CameraSettings(
    val aspectRatio: Int = SettingsDefaults.ASPECT_RATIO,
    val gridType: GridType = SettingsDefaults.GRID_TYPE,
    val focusTimeoutSeconds: Long = SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
    val selfTimerDurationSeconds: Int = SettingsDefaults.SELF_TIMER_DURATION,
    val enableCameraSounds: Boolean = SettingsDefaults.CAMERA_SOUNDS,
    val includeAudio: Boolean = SettingsDefaults.INCLUDE_AUDIO,
    val enableEis: Boolean = SettingsDefaults.ENABLE_EIS,
    val enableZsl: Boolean = SettingsDefaults.ENABLE_ZSL,
    val waitForFocusLock: Boolean = SettingsDefaults.WAIT_FOR_FOCUS_LOCK,
    val selectHighestResolution: Boolean = SettingsDefaults.SELECT_HIGHEST_RESOLUTION,
    val photoQuality: Int = SettingsDefaults.PHOTO_QUALITY,
    val removeExifAfterCapture: Boolean = SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
    val gyroscopeSuggestions: Boolean = SettingsDefaults.GYROSCOPE_SUGGESTIONS,
    val saveImageAsPreviewed: Boolean = SettingsDefaults.SAVE_IMAGE_AS_PREVIEW,
    val saveVideoAsPreviewed: Boolean = SettingsDefaults.SAVE_VIDEO_AS_PREVIEW,
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
        const val FOCUS_TIMEOUT_OFF = "Off"
    }
}

fun focusTimeoutLabel(seconds: Long): String {
    return when (seconds) {
        0L -> CameraSettings.FOCUS_TIMEOUT_OFF
        else -> "${seconds}s"
    }
}
