package app.grapheneos.camera.ui.components.text

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText

@Immutable
internal class CenteredText(
    val layout: TextLayoutResult,
) {
    val inkOffsetY: Float = layout.inkCenteringOffsetY()
}

internal fun DrawScope.drawCenteredText(
    text: CenteredText,
    center: Offset,
    color: Color,
    alpha: Float = 1f,
) {
    drawText(
        textLayoutResult = text.layout,
        color = color,
        topLeft = Offset(
            x = center.x - text.layout.size.width / 2f,
            y = center.y - text.layout.size.height / 2f + text.inkOffsetY,
        ),
        alpha = alpha,
    )
}
