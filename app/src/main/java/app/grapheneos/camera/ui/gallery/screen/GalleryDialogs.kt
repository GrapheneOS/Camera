package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.runtime.Composable
import app.grapheneos.camera.ui.gallery.components.DeleteConfirmationSheet
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog as Dialog

@Composable
internal fun GalleryDialogs(
    dialog: Dialog?,
    onAction: (Action) -> Unit,
) {
    when (dialog) {
        is Dialog.DeleteConfirmation -> DeleteConfirmationSheet(
            onConfirm = { onAction(Action.DeleteConfirmed) },
            onDismiss = { onAction(Action.DialogDismissed) },
        )

        null -> Unit
    }
}
