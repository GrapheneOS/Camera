package app.grapheneos.camera.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class QROverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val boxPaint: Paint = Paint().apply {
        color = 0Xffffff
        style = Paint.Style.STROKE
        strokeWidth = 4f * Resources.getSystem().displayMetrics.density
    }

    private val scrimPaint: Paint = Paint().apply {
        color = Color.parseColor("#99000000")
    }

    private val eraserPaint: Paint = Paint().apply {
        strokeWidth = boxPaint.strokeWidth
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val boxCornerRadius: Float =
        8f * Resources.getSystem().displayMetrics.density

    private var boxRect: RectF? = null

    fun setViewFinder() {
        val overlayWidth = width.toFloat()
        val overlayHeight = height.toFloat()
        val boxWidth = overlayWidth * 80 / 100
        val boxHeight = overlayHeight * 36 / 100
        val cx = overlayWidth / 2
        val cy = overlayHeight / 2
        boxRect = RectF(cx - boxWidth / 2, cy - boxHeight / 2, cx + boxWidth / 2, cy + boxHeight / 2)

        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        boxRect?.let {
            // Draws the dark background scrim and leaves the box area clear.
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            // As the stroke is always centered, so erase twice with FILL and STROKE respectively to clear
            // all area that the box rect would occupy.
            eraserPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(it, boxCornerRadius, boxCornerRadius, eraserPaint)
            eraserPaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(it, boxCornerRadius, boxCornerRadius, eraserPaint)
            // Draws the box.
            canvas.drawRoundRect(it, boxCornerRadius, boxCornerRadius, boxPaint)
        }
    }

}