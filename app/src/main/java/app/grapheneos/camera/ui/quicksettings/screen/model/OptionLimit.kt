package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit

@Immutable
internal sealed interface OptionLimit {

    data object LowLightBoostAtHighFrameRate : OptionLimit

    data class HighFrameRate(
        val limit: HighFrameRateLimit,
    ) : OptionLimit
}
