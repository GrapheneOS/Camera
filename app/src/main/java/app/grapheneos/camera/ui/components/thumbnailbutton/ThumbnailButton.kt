package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.components.squarebutton.SQUARE_BUTTON_SHAPE
import app.grapheneos.camera.ui.components.squarebutton.SquareButton
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.PREVIEW_WARM_ICON

private const val PREVIEW_SHOT_SIZE = 96

/**
 * @param image the latest shot. A new instance slides in from the top and pushes the shown one out
 * at the bottom; passing the same instance again keeps it still.
 */
@Composable
internal fun ThumbnailButton(
    image: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progress: RingProgress = RingProgress.None,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    colors: ThumbnailButtonColors = ThumbnailButtonColors.fromTheme(),
    interactionSource: MutableInteractionSource? = null,
) {
    SquareButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        colors = colors.buttonColors,
        interactionSource = interactionSource,
    ) {
        ThumbnailButtonImage(
            image = image,
            buttonShape = SQUARE_BUTTON_SHAPE,
            modifier = Modifier.matchParentSize(),
        )
        ThumbnailButtonRing(
            progress = progress,
            buttonShape = SQUARE_BUTTON_SHAPE,
            color = colors.progressColor,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@PreviewLightDark
@Composable
private fun ThumbnailButtonPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewThumbnailButtonRow(colors = ThumbnailButtonColors.fromTheme())
            PreviewThumbnailButtonRow(colors = ThumbnailButtonColors.fromSurfaceTheme())
        }
    }
}

@Composable
private fun PreviewThumbnailButtonRow(
    colors: ThumbnailButtonColors,
) {
    val shots = remember {
        listOf(
            previewShot(top = PREVIEW_WARM_ICON, bottom = PREVIEW_SCENE),
            previewShot(top = PREVIEW_COOL_ICON, bottom = PREVIEW_SCENE),
            previewShot(top = PREVIEW_COOL_ICON, bottom = PREVIEW_WARM_ICON),
        )
    }
    var shotIndex by remember { mutableIntStateOf(0) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        ThumbnailButton(
            image = null,
            onClick = {},
            colors = colors,
        )
        ThumbnailButton(
            image = shots[shotIndex],
            onClick = { shotIndex = (shotIndex + 1) % shots.size },
            colors = colors,
        )
        ThumbnailButton(
            image = shots[1],
            onClick = {},
            progress = RingProgress.Determinate(fraction = { 0.6f }),
            colors = colors,
        )
        ThumbnailButton(
            image = shots[2],
            onClick = {},
            progress = RingProgress.Indeterminate,
            colors = colors,
        )
    }
}

private fun previewShot(
    top: Color,
    bottom: Color,
): ImageBitmap {
    val image = ImageBitmap(
        width = PREVIEW_SHOT_SIZE,
        height = PREVIEW_SHOT_SIZE,
    )
    val paint = Paint().apply {
        shader = LinearGradientShader(
            from = Offset.Zero,
            to = Offset(x = 0f, y = PREVIEW_SHOT_SIZE.toFloat()),
            colors = listOf(top, bottom),
        )
    }

    Canvas(image).drawRect(
        left = 0f,
        top = 0f,
        right = PREVIEW_SHOT_SIZE.toFloat(),
        bottom = PREVIEW_SHOT_SIZE.toFloat(),
        paint = paint,
    )

    return image
}
