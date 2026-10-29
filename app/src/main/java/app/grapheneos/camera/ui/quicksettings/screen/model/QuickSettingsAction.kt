package app.grapheneos.camera.ui.quicksettings.screen.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import kotlin.time.Duration

internal sealed interface QuickSettingsAction {

    data object CameraSettingsClicked : QuickSettingsAction

    data object CameraSettingsLongClicked : QuickSettingsAction

    data class FlashModeSelected(
        val mode: FlashMode,
    ) : QuickSettingsAction

    data class TorchToggled(
        val enabled: Boolean,
    ) : QuickSettingsAction

    data class SelfIlluminationToggled(
        val enabled: Boolean,
    ) : QuickSettingsAction

    data class SelfTimerSelected(
        val duration: Duration,
    ) : QuickSettingsAction

    data class AspectRatioSelected(
        val ratio: AspectRatio,
    ) : QuickSettingsAction

    data class VideoQualitySelected(
        val quality: VideoQuality,
    ) : QuickSettingsAction

    data class AudioToggled(
        val enabled: Boolean,
    ) : QuickSettingsAction
}
