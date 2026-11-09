package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn

@Composable
internal fun BoxScope.PreviewLayer(
    bounds: DpRect,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .align(AbsoluteAlignment.TopLeft)
            .absoluteOffset(
                x = bounds.left,
                y = bounds.top,
            )
            .size(
                width = bounds.width,
                height = bounds.height,
            )
            .clipToBounds(),
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun PreviewLayerPreview() {
    CameraPreviewColumn {
        Box(
            modifier = Modifier.size(
                width = 280.dp,
                height = 400.dp,
            ),
        ) {
            PreviewLayer(
                bounds = DpRect(
                    left = 20.dp,
                    top = 40.dp,
                    right = 260.dp,
                    bottom = 360.dp,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}
