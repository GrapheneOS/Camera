package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpRect
import app.grapheneos.camera.ui.previewoverlays.components.PreviewLayer

@Composable
internal fun PreviewOverlaysScreen(
    previewBounds: DpRect,
    modifier: Modifier = Modifier,
) {
    PreviewOverlaysContent(
        previewBounds = previewBounds,
        modifier = modifier,
    )
}

@Composable
private fun PreviewOverlaysContent(
    previewBounds: DpRect,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        PreviewLayer(bounds = previewBounds) {}
    }
}
