package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.runtime.Composable
import app.grapheneos.camera.ui.gallery.components.DeleteConfirmationSheet
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog as Dialog
import app.grapheneos.camera.ui.gallery.screen.model.GalleryMenuUiModel
import app.grapheneos.camera.ui.mediaviewer.components.video.PlaybackSpeedSheet

@Composable
internal fun GalleryDialogs(
    dialog: Dialog?,
    menu: GalleryMenuUiModel?,
    onAction: (Action) -> Unit,
) {
    when (dialog) {
        is Dialog.DeleteConfirmation -> DeleteConfirmationSheet(
            onConfirm = { onAction(Action.DeleteConfirmed) },
            onDismiss = { onAction(Action.DialogDismissed) },
        )

        is Dialog.PlaybackSpeed -> (menu as? GalleryMenuUiModel.Video)?.let {
            PlaybackSpeedSheet(
                speed = it.playbackSpeed,
                onSpeedSelect = { speed -> onAction(Action.PlaybackSpeedSelected(speed)) },
                onDismiss = { onAction(Action.DialogDismissed) },
            )
        }

        null -> Unit
    }
}
