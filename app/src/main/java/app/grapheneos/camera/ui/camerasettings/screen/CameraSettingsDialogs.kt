package app.grapheneos.camera.ui.camerasettings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.ui.camerasettings.components.PhotoQualityDialog
import app.grapheneos.camera.ui.camerasettings.components.PhotoQualityOption
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsDialog as Dialog
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun CameraSettingsDialogs(
    uiState: UiState,
    onAction: (Action) -> Unit,
) {
    when (uiState.dialog) {
        is Dialog.PhotoQuality -> PhotoQualityDialog(
            quality = uiState.photoQuality.quality,
            options = photoQualityOptions(uiState.photoQuality.levels),
            onConfirm = { onAction(Action.PhotoQualityConfirmed(it)) },
            onDismiss = { onAction(Action.DialogDismissed) },
        )

        null -> Unit
    }
}

@Composable
private fun photoQualityOptions(
    levels: ImmutableList<PhotoQualityLevel>,
): ImmutableList<PhotoQualityOption> {
    return levels
        .map { level ->
            PhotoQualityOption(
                quality = level.quality,
                label = stringResource(level.label),
            )
        }
        .toImmutableList()
}
