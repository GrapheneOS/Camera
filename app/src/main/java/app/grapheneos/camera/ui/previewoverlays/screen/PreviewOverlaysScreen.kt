package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.grapheneos.camera.ui.components.compositiongrid.CompositionGrid
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import app.grapheneos.camera.ui.previewoverlays.components.PreviewControls
import app.grapheneos.camera.ui.previewoverlays.components.PreviewFocus
import app.grapheneos.camera.ui.previewoverlays.components.PreviewLayer
import app.grapheneos.camera.ui.previewoverlays.components.PreviewLevel
import app.grapheneos.camera.ui.previewoverlays.components.SelfTimerCountdown
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.LifecycleAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.FocusUiModel
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val CONTROLS_GAP = 24.dp

@Composable
internal fun PreviewOverlaysScreen(
    previewBounds: DpRect,
    shutterRowInset: Dp,
    modifier: Modifier = Modifier,
    screenModel: PreviewOverlaysScreenModel = viewModel<PreviewOverlaysViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val level = screenModel.levelUiState.collectAsStateWithLifecycle()
    val zoomRatio = screenModel.zoomRatio.collectAsStateWithLifecycle()

    LifecycleResumeEffect(screenModel) {
        screenModel.onAction(LifecycleAction.ScreenResumed)
        onPauseOrDispose { screenModel.onAction(LifecycleAction.ScreenPaused) }
    }

    PreviewOverlaysContent(
        uiState = uiState,
        level = level,
        zoomRatio = { zoomRatio.value },
        previewBounds = previewBounds,
        shutterRowInset = shutterRowInset,
        modifier = modifier,
        onAction = screenModel::onAction,
    )
}

@Composable
private fun PreviewOverlaysContent(
    uiState: PreviewOverlaysUiState,
    level: State<LevelUiState>,
    zoomRatio: () -> Float,
    previewBounds: DpRect,
    shutterRowInset: Dp,
    modifier: Modifier = Modifier,
    onAction: (Action) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        PreviewIndicators(
            gridPattern = uiState.gridPattern,
            focus = uiState.focus,
            selfTimerRemaining = uiState.selfTimerRemaining,
            level = level,
            previewBounds = previewBounds,
        )
        PreviewControls(
            control = uiState.control,
            zoomRatio = zoomRatio,
            onAction = onAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = shutterRowInset + CONTROLS_GAP),
        )
    }
}

@Composable
private fun BoxScope.PreviewIndicators(
    gridPattern: CompositionGridPattern?,
    focus: FocusUiModel?,
    selfTimerRemaining: Duration?,
    level: State<LevelUiState>,
    previewBounds: DpRect,
) {
    PreviewLayer(bounds = previewBounds) {
        gridPattern?.let { pattern ->
            CompositionGrid(
                pattern = pattern,
                modifier = Modifier.matchParentSize(),
            )
        }
        PreviewLevel(
            level = level,
            modifier = Modifier.align(Alignment.Center),
        )
        focus?.let { visibleFocus ->
            PreviewFocus(focus = visibleFocus)
        }
        selfTimerRemaining?.let { remaining ->
            SelfTimerCountdown(
                remaining = remaining,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewOverlaysPreview() {
    CameraPreviewColumn {
        CameraPreviewViewfinder(
            modifier = Modifier.size(
                width = 240.dp,
                height = 320.dp,
            ),
        ) {
            PreviewOverlaysContent(
                uiState = PreviewOverlaysUiState(
                    gridPattern = CompositionGridPattern.GoldenRatio,
                    selfTimerRemaining = 3.seconds,
                ),
                level = rememberUpdatedState(LevelUiState()),
                zoomRatio = { 1f },
                previewBounds = DpRect(
                    left = 0.dp,
                    top = 0.dp,
                    right = 240.dp,
                    bottom = 320.dp,
                ),
                shutterRowInset = 0.dp,
            )
        }
    }
}
