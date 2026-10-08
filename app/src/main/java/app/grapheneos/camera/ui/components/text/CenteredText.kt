package app.grapheneos.camera.ui.components.text

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText

/**
 * @param centeredLength how many characters from the start are centered; the rest hangs past the
 * center, as a unit sign does after its number.
 */
@Immutable
internal class CenteredText(
    val layout: TextLayoutResult,
    centeredLength: Int = layout.layoutInput.text.length,
) {
    val inkOffsetY: Float = layout.inkCenteringOffsetY()
    val offsetX: Float = when (centeredLength) {
        layout.layoutInput.text.length -> 0f
        else -> layout.size.width / 2f - layout.rangeCenterX(end = centeredLength)
    }
}

internal fun DrawScope.drawCenteredText(
    text: CenteredText,
    center: Offset,
    color: Color,
    alpha: Float = 1f,
    shadow: Shadow? = null,
) {
    drawText(
        textLayoutResult = text.layout,
        color = color,
        topLeft = Offset(
            x = center.x - text.layout.size.width / 2f + text.offsetX,
            y = center.y - text.layout.size.height / 2f + text.inkOffsetY,
        ),
        alpha = alpha,
        shadow = shadow,
    )
}
