package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import app.grapheneos.camera.ui.core.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.cameraColors
import app.grapheneos.camera.ui.core.previewShot

private val PLAY_BADGE_SIZE = 64.dp
private val PLAY_ICON_SIZE = 40.dp

@Composable
internal fun VideoThumbnail(
    image: ImageBitmap,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.play_video),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.cameraColors.overlayScrim,
            contentColor = MaterialTheme.cameraColors.overlay,
            modifier = Modifier.size(PLAY_BADGE_SIZE),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(PLAY_ICON_SIZE),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun VideoThumbnailPreview() {
    CameraPreviewTheme {
        VideoThumbnail(
            image = previewShot(
                top = PREVIEW_COOL_ICON,
                bottom = PREVIEW_SCENE,
            ),
            onClick = {},
        )
    }
}
