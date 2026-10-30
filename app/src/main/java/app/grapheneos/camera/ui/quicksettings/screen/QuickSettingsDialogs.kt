package app.grapheneos.camera.ui.quicksettings.screen

import androidx.compose.runtime.Composable
import app.grapheneos.camera.ui.quicksettings.components.HighFrameRateUnavailableDialog
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsDialog as Dialog

@Composable
internal fun QuickSettingsDialogs(
    dialog: Dialog?,
    onAction: (Action) -> Unit,
) {
    when (dialog) {
        is Dialog.HighFrameRateUnavailable -> HighFrameRateUnavailableDialog(
            limit = dialog.limit,
            onDismiss = { onAction(Action.DialogDismissed) },
        )

        null -> Unit
    }
}
