package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal sealed interface QuickSettingsDialog {

    data class OptionUnavailable(
        val limit: OptionLimit,
    ) : QuickSettingsDialog
}
