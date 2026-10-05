package app.grapheneos.camera.ui.components.compositiongrid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.CameraPreviewViewfinder
import app.grapheneos.camera.ui.core.PREVIEW_ASPECT_RATIO
import app.grapheneos.camera.ui.core.PREVIEW_TALL_ASPECT_RATIO
import kotlin.math.roundToInt

@Composable
internal fun CompositionGrid(
    pattern: CompositionGridPattern,
    modifier: Modifier = Modifier,
    colors: CompositionGridColors = CompositionGridColors.fromTheme(),
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val columns = pattern.lineOffsets(length = size.width.roundToInt())
            val rows = pattern.lineOffsets(length = size.height.roundToInt())

            onDrawBehind {
                drawCompositionGrid(
                    columns = columns,
                    rows = rows,
                    color = colors.lineColor,
                )
            }
        },
    )
}

private fun DrawScope.drawCompositionGrid(
    columns: FloatArray,
    rows: FloatArray,
    color: Color,
) {
    columns.forEach { x ->
        drawLine(
            color = color,
            start = Offset(x = x, y = 0f),
            end = Offset(x = x, y = size.height),
            strokeWidth = Stroke.HairlineWidth,
        )
    }
    rows.forEach { y ->
        drawLine(
            color = color,
            start = Offset(x = 0f, y = y),
            end = Offset(x = size.width, y = y),
            strokeWidth = Stroke.HairlineWidth,
        )
    }
}

@PreviewLightDark
@Composable
private fun CompositionGridPreview() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewCompositionGridRow(aspectRatio = PREVIEW_ASPECT_RATIO)
            PreviewCompositionGridRow(aspectRatio = PREVIEW_TALL_ASPECT_RATIO)
        }
    }
}

@Composable
private fun PreviewCompositionGridRow(
    aspectRatio: Float,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        CompositionGridPattern.entries.forEach { pattern ->
            CameraPreviewViewfinder(
                modifier = Modifier.weight(weight = 1f),
                aspectRatio = aspectRatio,
            ) {
                CompositionGrid(
                    pattern = pattern,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}
