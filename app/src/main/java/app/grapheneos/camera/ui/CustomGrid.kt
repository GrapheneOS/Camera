package app.grapheneos.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.AspectRatio
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.ui.activities.MainActivity

class CustomGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint: Paint = Paint()
    private lateinit var mActivity: MainActivity

    fun setMainActivity(mActivity: MainActivity) {
        this.mActivity = mActivity
    }

    init {
        paint.isAntiAlias = true
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(255, 255, 255, 255)
    }

    override fun onDraw(canvas: Canvas) {
        val camConfig = mActivity.camConfig

        super.onDraw(canvas)

        if (camConfig.gridType == CamConfig.GridType.NONE) {
            return
        }

        if (camConfig.gridType == CamConfig.GridType.GOLDEN_RATIO) {

            val cx = width / 2f
            val cy = height / 2f

            val dxH = width / 8f
            val dyH = height / 8f

            canvas.drawLine(cx - dxH, 0f, cx - dxH, height.toFloat(), paint)
            canvas.drawLine(cx + dxH, 0f, cx + dxH, height.toFloat(), paint)
            canvas.drawLine(0f, cy - dyH, width.toFloat(), cy - dyH, paint)
            canvas.drawLine(0f, cy + dyH, width.toFloat(), cy + dyH, paint)

        } else {

            val seed = if (camConfig.gridType == CamConfig.GridType.THREE_BY_THREE) {
                3f
            } else {
                4f
            }

            canvas.drawLine(
                width / seed * 2f,
                0f,
                width / seed * 2f,
                height.toFloat(),
                paint
            )
            canvas.drawLine(width / seed, 0f, width / seed, height.toFloat(), paint)
            canvas.drawLine(
                0f, height / seed * 2f,
                width.toFloat(), height / seed * 2f, paint
            )
            canvas.drawLine(0f, height / seed, width.toFloat(), height / seed, paint)

            if (seed == 4f) {
                canvas.drawLine(
                    width / seed * 3f,
                    0f,
                    width / seed * 3f,
                    height.toFloat(),
                    paint
                )
                canvas.drawLine(
                    0f, height / seed * 3f,
                    width.toFloat(), height / seed * 3f, paint
                )
            }
        }
    }
}
