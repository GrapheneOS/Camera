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
    trashedPageKey: String?,
    snackbarHostState: SnackbarHostState,
    onUndoClick: () -> Unit,
    onUndoDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val currentOnUndoClick by rememberUpdatedState(onUndoClick)
    val currentOnUndoDismiss by rememberUpdatedState(onUndoDismiss)

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect

        snackbarHostState.showSnackbar(
            message = resources.getString(message.text),
            duration = SnackbarDuration.Short,
        )
    }

    LaunchedEffect(trashedPageKey) {
        if (trashedPageKey == null) return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.deleted_successfully),
            actionLabel = resources.getString(R.string.undo),
            duration = SnackbarDuration.Short,
        )

        when (result) {
            SnackbarResult.ActionPerformed -> currentOnUndoClick()
            SnackbarResult.Dismissed -> currentOnUndoDismiss()
        }
    }
}
