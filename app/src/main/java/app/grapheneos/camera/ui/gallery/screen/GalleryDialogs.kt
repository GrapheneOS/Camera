package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.runtime.Composable
import app.grapheneos.camera.ui.gallery.components.DeleteConfirmationSheet
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.DeletionAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.PlaybackOptionsAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.ScreenAction
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
            onConfirm = { onAction(DeletionAction.DeleteConfirmed) },
            onDismiss = { onAction(ScreenAction.DialogDismissed) },
        )

        is Dialog.PlaybackSpeed -> (menu as? GalleryMenuUiModel.Video)?.let {
            PlaybackSpeedSheet(
                speed = it.playbackSpeed,
                onSpeedSelect = { speed ->
                    onAction(PlaybackOptionsAction.PlaybackSpeedSelected(speed))
                },
                onDismiss = { onAction(ScreenAction.DialogDismissed) },
            )
        }

        null -> Unit
    }
}
