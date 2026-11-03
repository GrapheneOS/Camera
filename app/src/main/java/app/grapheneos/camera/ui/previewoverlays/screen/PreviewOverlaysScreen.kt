package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.grapheneos.camera.ui.components.compositiongrid.CompositionGrid
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import app.grapheneos.camera.ui.previewoverlays.components.PreviewLayer
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState

@Composable
internal fun PreviewOverlaysScreen(
    previewBounds: DpRect,
    modifier: Modifier = Modifier,
    screenModel: PreviewOverlaysScreenModel = viewModel<PreviewOverlaysViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()

    PreviewOverlaysContent(
        uiState = uiState,
        previewBounds = previewBounds,
        modifier = modifier,
    )
}

@Composable
private fun PreviewOverlaysContent(
    uiState: PreviewOverlaysUiState,
    previewBounds: DpRect,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        PreviewLayer(bounds = previewBounds) {
            if (uiState.isGridVisible) {
                CompositionGrid(
                    pattern = uiState.gridPattern,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewOverlaysPreview() {
    CameraPreviewColumn {
        CameraPreviewViewfinder(
            modifier = Modifier.size(width = 240.dp, height = 320.dp),
        ) {
            PreviewOverlaysContent(
                uiState = PreviewOverlaysUiState(
                    gridPattern = CompositionGridPattern.GoldenRatio,
                    isGridVisible = true,
                ),
                previewBounds = DpRect(
                    left = 0.dp,
                    top = 0.dp,
                    right = 240.dp,
                    bottom = 320.dp,
                ),
            )
        }
    }
}
