package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.motion.POP_IN
import app.grapheneos.camera.ui.components.motion.POP_OUT
import app.grapheneos.camera.ui.components.valuechip.ValueChip
import app.grapheneos.camera.ui.components.zoom.ZoomControl
import app.grapheneos.camera.ui.components.zoom.rememberZoomFormat
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ZoomAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.ZoomUiModel
import kotlinx.collections.immutable.persistentListOf

private val VALUE_SPACING = 12.dp

@Composable
internal fun PreviewZoom(
    zoom: ZoomUiModel,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suffix = stringResource(R.string.zoom_value_suffix)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = VALUE_SPACING),
    ) {
        AnimatedVisibility(
            visible = zoom.isExpanded,
            enter = POP_IN,
            exit = POP_OUT,
        ) {
            ValueChip(text = rememberZoomFormat().format(value = zoom.ratio) + suffix)
        }
        ZoomControl(
            value = zoom.ratio,
            onValueChange = { ratio -> onAction(Action.RatioChanged(ratio)) },
            valueRange = zoom.range,
            stops = zoom.presets,
            valueSuffix = suffix,
            expanded = zoom.isExpanded,
            showCurrentValue = zoom.isCurrentValueShown,
            animateValueChanges = !zoom.isPinching,
            expandLabel = stringResource(R.string.zoom_expand),
            onExpand = { onAction(Action.Expanded) },
            onStopClick = { ratio -> onAction(Action.PresetSelected(ratio)) },
            onPressChanged = { pressed ->
                onAction(Action.ControlPressChanged(isPressed = pressed))
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewZoomPreview() {
    CameraPreviewViewfinder {
        PreviewZoom(
            zoom = ZoomUiModel(
                ratio = 1.5f,
                range = 0.5f..8f,
                presets = persistentListOf(0.5f, 1f, 2f),
                isExpanded = true,
            ),
            onAction = {},
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
