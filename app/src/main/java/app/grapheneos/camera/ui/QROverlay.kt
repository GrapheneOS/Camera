package app.grapheneos.camera.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class QROverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {
    companion object {
        const val RATIO = 0.6f
    }

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

    var size: Float = 0f
        private set

    private fun setViewFinder() {
        val overlayWidth = width.toFloat()
        val overlayHeight = height.toFloat()

        size = overlayHeight.coerceAtMost(overlayWidth) * RATIO

        val cx = overlayWidth / 2
        val cy = overlayHeight / 2
        boxRect = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        setViewFinder()
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
