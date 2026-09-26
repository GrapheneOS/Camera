package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.settings.model.SettingsDefaults

data class CaptureUiState(
    val geoTagging: Boolean = false,
    val includeAudio: Boolean = SettingsDefaults.INCLUDE_AUDIO,
    val cameraSounds: Boolean = SettingsDefaults.CAMERA_SOUNDS,
)
