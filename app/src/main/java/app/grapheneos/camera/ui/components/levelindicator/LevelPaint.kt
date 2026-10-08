package app.grapheneos.camera.ui.components.levelindicator

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

internal class LevelPaint(
    private val shadowColor: Color,
    private val shadowRadius: Float,
) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    fun drawLine(
        canvas: Canvas,
        start: Offset,
        end: Offset,
        width: Float,
        color: Color,
        alpha: Float,
    ) {
        prepare(
            color = color,
            alpha = alpha,
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        canvas.nativeCanvas.drawLine(start.x, start.y, end.x, end.y, paint)
    }

    fun drawDot(
        canvas: Canvas,
        center: Offset,
        radius: Float,
        color: Color,
        alpha: Float,
    ) {
        prepare(
            color = color,
            alpha = alpha,
        )
        paint.style = Paint.Style.FILL
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)
    }

    private fun prepare(
        color: Color,
        alpha: Float,
    ) {
        paint.color = color.copy(alpha = color.alpha * alpha).toArgb()
        paint.setShadowLayer(
            shadowRadius,
            0f,
            0f,
            shadowColor.copy(alpha = shadowColor.alpha * alpha).toArgb(),
        )
    }
}
