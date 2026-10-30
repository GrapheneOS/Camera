package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit

@Immutable
internal sealed interface QuickSettingsDialog {

    data class HighFrameRateUnavailable(
        val limit: HighFrameRateLimit,
    ) : QuickSettingsDialog
}
