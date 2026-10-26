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
import app.grapheneos.camera.ui.gallery.components.bars.CaptureTimeTitle
import app.grapheneos.camera.ui.gallery.components.bars.GalleryMenu
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.DetailsAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.OtherAppAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction.PlaybackOptionsAction
import app.grapheneos.camera.ui.gallery.screen.model.GalleryBarsUiModel
import app.grapheneos.camera.ui.mediaviewer.components.ViewerTopBar

@Composable
internal fun GalleryTopBar(
    bars: GalleryBarsUiModel,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    ViewerTopBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        navigation = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = stringResource(R.string.gallery_back_to_camera),
                )
            }
            if (bars.showsSystemGallery) {
                IconButton(onClick = { onAction(OtherAppAction.SystemGalleryClicked) }) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = stringResource(R.string.open_gallery),
                    )
                }
            }
        },
        title = {
            bars.capturedAt?.let {
                CaptureTimeTitle(
                    capturedAt = it,
                    onClick = when {
                        bars.showsSystemGallery -> {
                            { onAction(OtherAppAction.CaptureTimeClicked) }
                        }
                        else -> null
                    },
                )
            }
        },
        actions = {
            bars.menu?.let { menu ->
                GalleryMenu(
                    menu = menu,
                    showsExternalActions = bars.showsExternalActions,
                    onInfoClick = { onAction(DetailsAction.DetailsShownChanged(isShown = true)) },
                    onEditWithClick = { onAction(OtherAppAction.EditWithClicked) },
                    onUseAsClick = { onAction(OtherAppAction.UseAsClicked) },
                    onLoopClick = { onAction(PlaybackOptionsAction.LoopClicked) },
                    onPlaybackSpeedClick = { onAction(PlaybackOptionsAction.PlaybackSpeedClicked) },
                )
            }
        },
    )
}
