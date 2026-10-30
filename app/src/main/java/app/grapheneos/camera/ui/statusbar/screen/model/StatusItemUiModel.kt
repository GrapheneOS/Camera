package app.grapheneos.camera.ui.statusbar.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import kotlin.time.Duration

@Immutable
internal sealed interface StatusItemUiModel {

    data object VideoLight : StatusItemUiModel

    data object SelfIllumination : StatusItemUiModel

    data class Flash(
        val mode: FlashMode,
    ) : StatusItemUiModel

    data class SelfTimer(
        val duration: Duration,
    ) : StatusItemUiModel

    data class Resolution(
        val quality: VideoQuality,
    ) : StatusItemUiModel
}
