package app.grapheneos.camera.ui.common.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val CROSS_INSET = 0.15f
private const val CROSS_GAP_RATIO = 2.5f

private val CROSS_STROKE_WIDTH = 2.dp

internal fun Modifier.copyOnLongClick(
    text: String,
    copyLabel: String,
    onCopy: (String) -> Unit,
    onClickLabel: String? = null,
    onClick: () -> Unit = {},
): Modifier {
    return combinedClickable(
        onClickLabel = onClickLabel,
        onLongClickLabel = copyLabel,
        onLongClick = { onCopy(text) },
        onClick = onClick,
    )
}

internal fun Modifier.crossedOut(
    isCrossedOut: Boolean,
    color: Color,
): Modifier {
    return when {
        isCrossedOut -> graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                val strokeWidth = CROSS_STROKE_WIDTH.toPx()
                val start = Offset(
                    x = size.width * CROSS_INSET,
                    y = size.height * CROSS_INSET,
                )
                val end = Offset(
                    x = size.width * (1f - CROSS_INSET),
                    y = size.height * (1f - CROSS_INSET),
                )

                drawContent()
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth * CROSS_GAP_RATIO,
                    blendMode = BlendMode.Clear,
                )
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth,
                )
            }
        else -> this
    }
}
