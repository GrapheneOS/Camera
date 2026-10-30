package app.grapheneos.camera.ui.quicksettings.screen.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import kotlin.time.Duration

internal sealed interface QuickSettingsAction {

    data object CameraSettingsClicked : QuickSettingsAction

    data object CameraSettingsLongClicked : QuickSettingsAction

    data object HighFrameRateLimitClicked : QuickSettingsAction

    data object DialogDismissed : QuickSettingsAction

    data class FlashModeSelected(
        val mode: FlashMode,
    ) : QuickSettingsAction

    data class VideoLightToggled(
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

    data class VideoFrameRateSelected(
        val frameRate: VideoFrameRate,
    ) : QuickSettingsAction

    data class AudioToggled(
        val enabled: Boolean,
    ) : QuickSettingsAction
}
