package app.grapheneos.camera.ui.mediaviewer.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.core.preview.PreviewScreenLightDark
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection
import app.grapheneos.camera.ui.mediaviewer.playback.VideoSkipUiModel
import kotlin.time.Duration.Companion.seconds

internal const val VIDEO_SKIP_AREA_FRACTION = 1f / 3f

private const val INDICATOR_ALPHA = 0.2f
private const val INDICATOR_VERTICAL_RADIUS_RATIO = 0.5f

private val INDICATOR_SPACING = 4.dp

@Composable
internal fun VideoSkipIndicator(
    skip: VideoSkipUiModel,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.cameraColors.overlay
    val seconds = skip.offset.inWholeSeconds.toInt()

    Column(
        verticalArrangement = Arrangement.spacedBy(
            space = INDICATOR_SPACING,
            alignment = Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(VIDEO_SKIP_AREA_FRACTION)
            .clipToBounds()
            .background(
                color = contentColor.copy(alpha = INDICATOR_ALPHA),
                shape = VideoSkipAreaShape(skip.direction),
            ),
    ) {
        Icon(
            imageVector = when (skip.direction) {
                SkipDirection.Back -> Icons.Outlined.FastRewind
                SkipDirection.Forward -> Icons.Outlined.FastForward
            },
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = pluralStringResource(R.plurals.video_skip_seconds, seconds, seconds),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal class VideoSkipAreaShape(
    private val direction: SkipDirection,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val isOnLeft = (direction == SkipDirection.Back) == (layoutDirection == LayoutDirection.Ltr)
        val centerX = when {
            isOnLeft -> 0f
            else -> size.width
        }
        val verticalRadius = size.height * INDICATOR_VERTICAL_RADIUS_RATIO
        val oval = Rect(
            left = centerX - size.width,
            top = size.height / 2f - verticalRadius,
            right = centerX + size.width,
            bottom = size.height / 2f + verticalRadius,
        )

        return Outline.Generic(Path().apply { addOval(oval) })
    }
}

@PreviewScreenLightDark
@Composable
private fun VideoSkipIndicatorPreview() {
    CameraPreviewTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.cameraColors.onOverlay),
        ) {
            VideoSkipIndicator(
                skip = VideoSkipUiModel(
                    direction = SkipDirection.Forward,
                    offset = 10.seconds,
                ),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
