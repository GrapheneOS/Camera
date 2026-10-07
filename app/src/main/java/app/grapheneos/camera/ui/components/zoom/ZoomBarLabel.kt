package app.grapheneos.camera.ui.components.zoom

import androidx.compose.ui.text.TextLayoutResult
import app.grapheneos.camera.ui.components.text.inkCenteringOffsetY

internal class ZoomBarLabel(
    val layout: TextLayoutResult,
) {
    val inkOffsetY: Float = layout.inkCenteringOffsetY()
}
