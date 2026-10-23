package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.preview.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.preview.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.preview.PreviewScreenLightDark
import app.grapheneos.camera.ui.core.preview.previewShot

@Composable
internal fun GalleryPhoto(
    image: ImageBitmap,
    onTap: () -> Unit,
    onDoubleTap: ((Offset) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val currentOnTap by rememberUpdatedState(onTap)

    Image(
        bitmap = image,
        contentDescription = stringResource(R.string.gallery_photo),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onDoubleTap) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = onDoubleTap,
                )
            },
    )
}

@PreviewScreenLightDark
@Composable
private fun GalleryPhotoPreview() {
    CameraPreviewTheme {
        GalleryPhoto(
            image = previewShot(
                top = PREVIEW_COOL_ICON,
                bottom = PREVIEW_SCENE,
            ),
            onTap = {},
            onDoubleTap = {},
        )
    }
}
