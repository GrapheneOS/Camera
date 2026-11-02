package app.grapheneos.camera.ui.viewfinder.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSeekBar
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import java.util.Locale
import kotlin.math.roundToInt

class ZoomBar : AppCompatSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    @SuppressLint("InflateParams")
    private var thumbView: View = LayoutInflater.from(context)
        .inflate(R.layout.zoom_bar_thumb, null, false)

    var onLinearZoomDragged: (Float) -> Unit = {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    private var renderedZoom: ZoomUiState? = null

    fun render(zoom: ZoomUiState) {
        if (zoom == renderedZoom) return
        renderedZoom = zoom

        progress = (zoom.linearZoom * 100).roundToInt()

        val textView: TextView = thumbView.findViewById(R.id.progress) as TextView
        val text = String.format(Locale.getDefault(), "%.1fx", zoom.zoomRatio)

        textView.text = text

        thumbView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val bitmap = Bitmap.createBitmap(
            thumbView.measuredWidth,
            thumbView.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        thumbView.layout(0, 0, thumbView.measuredWidth, thumbView.measuredHeight)
        thumbView.draw(canvas)
        thumb = BitmapDrawable(resources, bitmap)
        onSizeChanged(width, height, 0, 0)
    }

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(c: Canvas) {
        c.rotate(-90f)
        c.translate(-height.toFloat(), 0f)
        super.onDraw(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                var progress = max - (max * event.y / height).toInt()

                if (progress < 1) progress = 1
                if (progress > 100) progress = 100

                onLinearZoomDragged(progress / LINEAR_ZOOM_SCALE)
            }
            MotionEvent.ACTION_CANCEL -> {
            }
        }
        return true
    }

    private companion object {
        private const val LINEAR_ZOOM_SCALE = 100f
    }
}
