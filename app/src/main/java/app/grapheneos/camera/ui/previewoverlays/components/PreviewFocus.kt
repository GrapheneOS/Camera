package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.focusindicator.FOCUS_INDICATOR_SIZE
import app.grapheneos.camera.ui.components.focusindicator.FocusIndicator
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.FocusUiModel

@Composable
internal fun PreviewFocus(
    focus: FocusUiModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    key(focus.requestId) {
        val x = with(density) { focus.x.toDp() } - FOCUS_INDICATOR_SIZE / 2
        val y = with(density) { focus.y.toDp() } - FOCUS_INDICATOR_SIZE / 2
        FocusIndicator(
            visible = focus.visible,
            appearance = focus.appearance,
            lockIcon = Icons.Outlined.Lock,
            modifier = modifier.absoluteOffset(x = x, y = y),
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewFocusPreview() {
    val density = LocalDensity.current
    CameraPreviewViewfinder {
        PreviewFocus(
            focus = FocusUiModel(
                x = with(density) { 100.dp.toPx() },
                y = with(density) { 100.dp.toPx() },
                visible = true,
            ),
        )
    }
}
