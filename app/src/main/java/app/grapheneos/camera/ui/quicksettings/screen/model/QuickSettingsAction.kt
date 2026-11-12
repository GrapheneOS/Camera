package app.grapheneos.camera.ui.quicksettings.screen.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import kotlin.time.Duration

internal sealed interface QuickSettingsAction {

    data object LimitDismissed : QuickSettingsAction

    data class LimitClicked(
        val limit: OptionLimit,
    ) : QuickSettingsAction

    data class FlashModeSelected(
        val mode: FlashMode,
    ) : QuickSettingsAction

    data class SelfIlluminationToggled(
        val enabled: Boolean,
    ) : QuickSettingsAction

    sealed interface RequiresIdleRecorder : QuickSettingsAction {

        data object CameraSettingsClicked : RequiresIdleRecorder

        data object CameraSettingsLongClicked : RequiresIdleRecorder

        data class SelfTimerSelected(
            val duration: Duration,
        ) : RequiresIdleRecorder

        data class AspectRatioSelected(
            val ratio: AspectRatio,
        ) : RequiresIdleRecorder

        data class VideoQualitySelected(
            val quality: VideoQuality,
        ) : RequiresIdleRecorder

        data class VideoFrameRateSelected(
            val frameRate: VideoFrameRate,
        ) : RequiresIdleRecorder

        data class AudioToggled(
            val enabled: Boolean,
        ) : RequiresIdleRecorder
    }
}
