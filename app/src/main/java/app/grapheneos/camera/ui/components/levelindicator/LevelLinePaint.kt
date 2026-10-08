package app.grapheneos.camera.ui.components.levelindicator

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

internal class LevelLinePaint(
    shadowColor: Color,
    shadowRadius: Float,
) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(shadowRadius, 0f, 0f, shadowColor.toArgb())
    }

    fun drawLine(
        canvas: Canvas,
        start: Offset,
        end: Offset,
        width: Float,
        color: Color,
    ) {
        paint.strokeWidth = width
        paint.color = color.toArgb()
        canvas.nativeCanvas.drawLine(start.x, start.y, end.x, end.y, paint)
    }
}
