package app.grapheneos.camera.ui.quicksettings.screen.model

import app.grapheneos.camera.data.core.model.VideoQuality

sealed interface QuickSettingsAction {

    data object FlashToggleClicked : QuickSettingsAction

    data object TorchToggleClicked : QuickSettingsAction

    data object AspectRatioToggleClicked : QuickSettingsAction

    data object CameraSettingsClicked : QuickSettingsAction

    data class AudioToggled(
        val enabled: Boolean,
    ) : QuickSettingsAction

    data class SelfIlluminationToggled(
        val enabled: Boolean,
    ) : QuickSettingsAction

    data class SelfTimerSelected(
        val seconds: Int,
    ) : QuickSettingsAction

    data class VideoQualitySelected(
        val quality: VideoQuality,
    ) : QuickSettingsAction
}
