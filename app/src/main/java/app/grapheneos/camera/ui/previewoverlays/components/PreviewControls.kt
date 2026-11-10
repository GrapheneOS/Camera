package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.motion.revealResizing
import app.grapheneos.camera.ui.components.overlayiconbutton.OVERLAY_ICON_BUTTON_SIZE
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel
import kotlinx.collections.immutable.persistentListOf

private val VALUE_SPACING = 12.dp
private val RESET_BUTTON_GAP = 8.dp

@Composable
internal fun PreviewControls(
    control: PreviewControlUiModel?,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoom = control as? PreviewControlUiModel.Zoom
    val exposure = control as? PreviewControlUiModel.Exposure

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = VALUE_SPACING),
    ) {
        zoom?.let { visibleZoom ->
            PreviewZoomValue(zoom = visibleZoom)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            PreviewControlContainer(
                control = control,
                onAction = onAction,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = OVERLAY_ICON_BUTTON_SIZE + RESET_BUTTON_GAP * 2),
            )
            exposure?.let { visibleExposure ->
                PreviewExposureReset(
                    exposure = visibleExposure,
                    onAction = onAction,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = RESET_BUTTON_GAP),
                )
            }
        }
    }
}

@Composable
private fun PreviewControlContainer(
    control: PreviewControlUiModel?,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .background(color = MaterialTheme.cameraColors.overlayScrim),
        )
        AnimatedContent(
            targetState = control,
            transitionSpec = { revealResizing() },
            contentAlignment = Alignment.Center,
            contentKey = { visibleControl -> visibleControl?.let { it::class } },
        ) { visibleControl ->
            when (visibleControl) {
                is PreviewControlUiModel.Exposure -> PreviewExposure(
                    exposure = visibleControl,
                    onAction = onAction,
                    showContainer = false,
                )
                is PreviewControlUiModel.Zoom -> PreviewZoom(
                    zoom = visibleControl,
                    onAction = onAction,
                    showContainer = false,
                )
                null -> Unit
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewControlsPreview() {
    CameraPreviewViewfinder {
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            PreviewControls(
                control = PreviewControlUiModel.Zoom(
                    ratio = 1.5f,
                    range = 0.5f..8f,
                    presets = persistentListOf(0.5f, 1f, 2f),
                    isExpanded = true,
                ),
                onAction = {},
            )
            PreviewControls(
                control = PreviewControlUiModel.Exposure(
                    value = 0.5f,
                    range = -2f..2f,
                    steps = 23,
                    majorTickInterval = 6,
                    isResetVisible = true,
                ),
                onAction = {},
            )
        }
    }
}
