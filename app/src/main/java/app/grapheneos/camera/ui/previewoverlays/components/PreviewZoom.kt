package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.motion.POP_IN
import app.grapheneos.camera.ui.components.motion.POP_OUT
import app.grapheneos.camera.ui.components.valuechip.ValueChip
import app.grapheneos.camera.ui.components.zoom.ZoomControl
import app.grapheneos.camera.ui.components.zoom.rememberZoomFormat
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ZoomAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel

@Composable
internal fun PreviewZoom(
    zoom: PreviewControlUiModel.Zoom,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true,
) {
    ZoomControl(
        value = zoom.ratio,
        onValueChange = { ratio -> onAction(Action.RatioChanged(ratio)) },
        valueRange = zoom.range,
        stops = zoom.presets,
        valueSuffix = stringResource(R.string.zoom_value_suffix),
        expanded = zoom.isExpanded,
        showCurrentValue = zoom.isCurrentValueShown,
        showContainer = showContainer,
        animateValueChanges = !zoom.isPinching,
        expandLabel = stringResource(R.string.zoom_expand),
        onExpand = { onAction(Action.Expanded) },
        onStopClick = { ratio -> onAction(Action.PresetSelected(ratio)) },
        onPressChanged = { pressed ->
            onAction(Action.ControlPressChanged(isPressed = pressed))
        },
        modifier = modifier,
    )
}

@Composable
internal fun PreviewZoomValue(
    zoom: PreviewControlUiModel.Zoom,
    modifier: Modifier = Modifier,
) {
    val suffix = stringResource(R.string.zoom_value_suffix)
    val text = rememberZoomFormat().format(value = zoom.ratio) + suffix

    AnimatedVisibility(
        visible = zoom.isExpanded,
        modifier = modifier,
        enter = POP_IN,
        exit = POP_OUT,
    ) {
        ValueChip(text = text)
    }
}
