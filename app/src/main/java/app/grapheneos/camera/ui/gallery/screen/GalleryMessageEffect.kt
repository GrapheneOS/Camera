package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.gallery.screen.model.GalleryMessage

@Composable
internal fun GalleryMessageEffect(
    message: GalleryMessage?,
    snackbarHostState: SnackbarHostState,
    onUndoClick: () -> Unit,
    onUndoDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val currentOnUndoClick by rememberUpdatedState(onUndoClick)
    val currentOnUndoDismiss by rememberUpdatedState(onUndoDismiss)

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = resources.getString(message.text),
            actionLabel = when {
                message.offersUndo -> resources.getString(R.string.undo)
                else -> null
            },
            duration = SnackbarDuration.Short,
        )

        when {
            !message.offersUndo -> Unit
            result == SnackbarResult.ActionPerformed -> currentOnUndoClick()
            else -> currentOnUndoDismiss()
        }
    }
}
