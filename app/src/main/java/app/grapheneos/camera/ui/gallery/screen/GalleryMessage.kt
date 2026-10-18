package app.grapheneos.camera.ui.gallery.screen

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources

internal class GalleryMessage(
    @StringRes val text: Int,
)

@Composable
internal fun GalleryMessageEffect(
    message: GalleryMessage?,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(resources.getString(it.text)) }
    }
}
