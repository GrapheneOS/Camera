package app.grapheneos.camera.ui.gallery.components.pager

import android.view.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.mediaviewer.components.video.VideoSurface
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel

@Composable
internal fun GalleryPage(
    page: GalleryPageUiModel,
    preview: GalleryPreview,
    onTap: () -> Unit,
    onDoubleTap: ((Offset) -> Unit)?,
    video: VideoPlaybackUiModel?,
    onVideoSurfaceCreated: (Surface) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
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
        when {
            video is VideoPlaybackUiModel.Active -> PlayingVideo(
                video = video,
                preview = preview,
                onSurfaceCreated = onVideoSurfaceCreated,
                onSurfaceDestroyed = onVideoSurfaceDestroyed,
            )

            video is VideoPlaybackUiModel.Failed -> UnavailablePlaceholder(
                page = page,
            )

            preview is GalleryPreview.Loaded && page.isVideo -> VideoFrame(
                image = preview.image,
            )

            preview is GalleryPreview.Loaded -> GalleryPhoto(
                image = preview.image,
                onTap = onTap,
                onDoubleTap = onDoubleTap,
            )

            preview is GalleryPreview.Unavailable -> UnavailablePlaceholder(
                page = page,
            )
        }
    }
}

@Composable
private fun PlayingVideo(
    video: VideoPlaybackUiModel.Active,
    preview: GalleryPreview,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    val thumbnail = (preview as? GalleryPreview.Loaded)?.image

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        VideoSurface(
            aspectRatio = video.aspectRatio ?: thumbnail?.let { it.width.toFloat() / it.height },
            onSurfaceCreated = onSurfaceCreated,
            onSurfaceDestroyed = onSurfaceDestroyed,
        )
        if (!video.hasRenderedFrame && thumbnail != null) {
            VideoFrame(thumbnail)
        }
    }
}

@Composable
private fun VideoFrame(image: ImageBitmap) {
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun UnavailablePlaceholder(page: GalleryPageUiModel) {
    GalleryPlaceholder(
        icon = when {
            page.isVideo -> Icons.Outlined.VideocamOff
            else -> Icons.Outlined.BrokenImage
        },
        title = stringResource(
            when {
                page.isVideo -> R.string.inaccessible_video
                else -> R.string.inaccessible_image
            },
        ),
        description = stringResource(R.string.inaccessible_media_reason),
        fileName = page.name,
    )
}

@PreviewLightDark
@Composable
private fun GalleryPageUnavailablePreview() {
    CameraPreviewTheme {
        GalleryPage(
            page = GalleryPageUiModel(
                key = "content://media/external/video/media/1",
                name = "VID_20231016_120000_000",
                isVideo = true,
            ),
            preview = GalleryPreview.Unavailable,
            onTap = {},
            onDoubleTap = {},
            video = null,
            onVideoSurfaceCreated = {},
            onVideoSurfaceDestroyed = {},
        )
    }
}
