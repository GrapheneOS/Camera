package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.settings.model.SettingsDefaults

data class CaptureUiState(
    val canTakePicture: Boolean = false,
    val saveImageAsPreviewed: Boolean = SettingsDefaults.SAVE_IMAGE_AS_PREVIEW,
    val removeExifAfterCapture: Boolean = SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
    val geoTagging: Boolean = false,
    val includeAudio: Boolean = SettingsDefaults.INCLUDE_AUDIO,
    val cameraSounds: Boolean = SettingsDefaults.CAMERA_SOUNDS,
)
