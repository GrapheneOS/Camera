package app.grapheneos.camera.ui.gallery.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.gallery.components.CaptureTimeTitle
import app.grapheneos.camera.ui.gallery.components.GalleryMenu
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState as UiState
import app.grapheneos.camera.ui.mediaviewer.components.ViewerTopBar

@Composable
internal fun GalleryTopBar(
    uiState: UiState,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    ViewerTopBar(
        modifier = modifier,
        containerColor = containerColor,
        navigation = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = stringResource(R.string.gallery_back_to_camera),
                )
            }
            if (uiState.showsSystemGallery) {
                IconButton(onClick = { onAction(Action.SystemGalleryClicked) }) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = stringResource(R.string.open_gallery),
                    )
                }
            }
        },
        title = {
            uiState.capturedAt?.let {
                CaptureTimeTitle(
                    capturedAt = it,
                    onClick = when {
                        uiState.showsSystemGallery -> {
                            { onAction(Action.SystemGalleryClicked) }
                        }
                        else -> null
                    },
                )
            }
        },
        actions = {
            if (uiState.pages.isNotEmpty()) {
                GalleryMenu(
                    showsExternalActions = uiState.showsExternalActions,
                    onInfoClick = { onAction(Action.InfoClicked) },
                    onEditWithClick = { onAction(Action.EditWithClicked) },
                )
            }
        },
    )
}
