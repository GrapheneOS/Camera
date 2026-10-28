package app.grapheneos.camera.ui.viewfinder.screen

import androidx.compose.runtime.Composable
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.ui.viewfinder.components.dialog.CameraPermissionDialog
import app.grapheneos.camera.ui.viewfinder.components.dialog.MicrophonePermissionDialog
import app.grapheneos.camera.ui.viewfinder.components.dialog.StorageLocationNotFoundDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction as Action
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog as Dialog

@Composable
internal fun ViewfinderDialogs(
    dialog: Dialog?,
    onAction: (Action) -> Unit,
) {
    when (dialog) {
        is Dialog.Permission -> when (dialog.permission) {
            AppPermission.CAMERA -> CameraPermissionDialog(
                offersSettings = dialog.offersSettings,
                onSettingsClick = { onAction(PermissionAction.SettingsClicked) },
                onDismiss = { onAction(DialogAction.Dismissed) },
            )

            AppPermission.MICROPHONE -> MicrophonePermissionDialog(
                offersSettings = dialog.offersSettings,
                onSettingsClick = { onAction(PermissionAction.SettingsClicked) },
                onRecordWithoutAudioClick = { onAction(RecordingAction.RecordWithoutAudioClicked) },
                onDismiss = { onAction(DialogAction.Dismissed) },
            )

            AppPermission.LOCATION -> Unit
        }

        is Dialog.StorageLocationNotFound -> StorageLocationNotFoundDialog(
            onConfirm = { onAction(DialogAction.Dismissed) },
            onCameraSettingsClick = {
                onAction(DialogAction.Dismissed)
                onAction(SettingsAction.CameraSettingsClicked)
            },
        )

        null -> Unit
    }
}
