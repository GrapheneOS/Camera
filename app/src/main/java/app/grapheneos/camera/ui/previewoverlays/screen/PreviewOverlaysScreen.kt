package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.grapheneos.camera.ui.components.compositiongrid.CompositionGrid
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import app.grapheneos.camera.ui.previewoverlays.components.PreviewFocus
import app.grapheneos.camera.ui.previewoverlays.components.PreviewLayer
import app.grapheneos.camera.ui.previewoverlays.components.PreviewLevel
import app.grapheneos.camera.ui.previewoverlays.components.SelfTimerCountdown
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.LifecycleAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PreviewOverlaysScreen(
    previewBounds: DpRect,
    modifier: Modifier = Modifier,
    screenModel: PreviewOverlaysScreenModel = viewModel<PreviewOverlaysViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val level = screenModel.levelUiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(screenModel) {
        screenModel.onAction(LifecycleAction.ScreenResumed)
        onPauseOrDispose { screenModel.onAction(LifecycleAction.ScreenPaused) }
    }

    PreviewOverlaysContent(
        uiState = uiState,
        level = level,
        previewBounds = previewBounds,
        modifier = modifier,
    )
}

@Composable
private fun PreviewOverlaysContent(
    uiState: PreviewOverlaysUiState,
    level: State<LevelUiState>,
    previewBounds: DpRect,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        PreviewLayer(bounds = previewBounds) {
            uiState.gridPattern?.let { pattern ->
                CompositionGrid(
                    pattern = pattern,
                    modifier = Modifier.matchParentSize(),
                )
            }
            PreviewLevel(
                level = level,
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.focus?.let { focus ->
                PreviewFocus(focus = focus)
            }
            uiState.selfTimerRemaining?.let { remaining ->
                SelfTimerCountdown(
                    remaining = remaining,
                    modifier = Modifier.align(Alignment.Center),
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
                    selfTimerRemaining = 3.seconds,
                ),
                level = rememberUpdatedState(LevelUiState()),
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
