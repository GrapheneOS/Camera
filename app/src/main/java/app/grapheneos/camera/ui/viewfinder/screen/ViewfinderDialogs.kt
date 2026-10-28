package app.grapheneos.camera.ui.viewfinder.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.viewfinder.components.dialog.CameraPermissionDialog
import app.grapheneos.camera.ui.viewfinder.components.dialog.MicrophonePermissionDialog
import app.grapheneos.camera.ui.viewfinder.components.dialog.PictureFailureDetailsDialog
import app.grapheneos.camera.ui.viewfinder.components.dialog.PictureFailureDialog
import app.grapheneos.camera.ui.viewfinder.components.dialog.StorageLocationNotFoundDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureCause
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog as Dialog

@Composable
internal fun ViewfinderDialogs(
    dialog: Dialog?,
    onAction: (DialogAction) -> Unit,
) {
    val onDismiss = { onAction(DialogAction.Dismissed) }

    when (dialog) {
        is Dialog.CameraPermission -> CameraPermissionDialog(
            offersSettings = dialog.offersSettings,
            onSettingsClick = { onAction(DialogAction.Choice.PermissionSettingsClicked) },
            onDismiss = onDismiss,
        )

        is Dialog.MicrophonePermission -> MicrophonePermissionDialog(
            offersSettings = dialog.offersSettings,
            onSettingsClick = { onAction(DialogAction.Choice.PermissionSettingsClicked) },
            onRecordWithoutAudioClick = { onAction(DialogAction.Choice.RecordWithoutAudioClicked) },
            onDismiss = onDismiss,
        )

        is Dialog.StorageLocationNotFound -> StorageLocationNotFoundDialog(
            onDismiss = onDismiss,
            onCameraSettingsClick = { onAction(DialogAction.Choice.CameraSettingsClicked) },
        )

        is Dialog.PictureFailure -> when {
            dialog.showsDetails -> PictureFailureDetailsDialog(
                details = dialog.details,
                onCopyClick = { onAction(DialogAction.PictureFailureDetailsCopyClicked) },
                onDismiss = onDismiss,
            )

            else -> PictureFailureDialog(
                message = pictureFailureMessage(dialog.cause),
                onShowDetailsClick = { onAction(DialogAction.PictureFailureDetailsClicked) },
                onDismiss = onDismiss,
            )
        }

        null -> Unit
    }
}

@Composable
private fun pictureFailureMessage(cause: PictureFailureCause): String {
    return when (cause) {
        is PictureFailureCause.Capture -> {
            stringResource(R.string.unable_to_capture_image_verbose, cause.errorCode)
        }

        is PictureFailureCause.Save -> {
            stringResource(R.string.unable_to_save_image_verbose, cause.stage)
        }
    }
}
