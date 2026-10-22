package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview

@Composable
internal fun GalleryPage(
    page: GalleryPageUiModel,
    onTap: () -> Unit,
    onDoubleTap: ((Offset) -> Unit)?,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onTap,
            ),
    ) {
        when (val preview = page.preview) {
            is GalleryPreview.Loading -> Unit

            is GalleryPreview.Loaded -> when {
                page.isVideo -> VideoThumbnail(
                    image = preview.image,
                    onClick = onVideoClick,
                )
                else -> GalleryPhoto(
                    image = preview.image,
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                )
            }

            is GalleryPreview.Unavailable -> when {
                page.isVideo -> GalleryPlaceholder(
                    icon = Icons.Outlined.VideocamOff,
                    text = stringResource(R.string.inaccessible_video),
                )
                else -> GalleryPlaceholder(
                    icon = Icons.Outlined.BrokenImage,
                    text = stringResource(R.string.inaccessible_image),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun GalleryPageUnavailablePreview() {
    CameraPreviewTheme {
        GalleryPage(
            page = GalleryPageUiModel(
                key = "content://media/external/video/media/1",
                isVideo = true,
                preview = GalleryPreview.Unavailable,
            ),
            onTap = {},
            onDoubleTap = {},
            onVideoClick = {},
        )
    }
}
