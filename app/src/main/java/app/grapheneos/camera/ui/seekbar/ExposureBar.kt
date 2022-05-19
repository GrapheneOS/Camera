package app.grapheneos.camera.ui.seekbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.camera.core.ExposureState
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionManager
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity

class ExposureBar : AppCompatSeekBar {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    companion object {
        private const val PANEL_VISIBILITY_DURATION = 2000L
    }

    private val closePanelHandler: Handler = Handler(Looper.getMainLooper())

    private val closePanelRunnable = Runnable {
        hidePanel()
    }

    private lateinit var mainActivity: MainActivity

    fun setMainActivity(mainActivity: MainActivity) {
        this.mainActivity = mainActivity
    }

    fun setExposureConfig(exposureState: ExposureState) {
        max = exposureState.exposureCompensationRange.upper
        min = exposureState.exposureCompensationRange.lower

        incrementProgressBy(exposureState.exposureCompensationIndex)

        Log.i("TAG", "Setting progress from setExposureConfig")
        progress = (exposureState.exposureCompensationStep.numerator
                / exposureState.exposureCompensationStep.denominator) *
                exposureState.exposureCompensationIndex

        onSizeChanged(width, height, 0, 0)
    }

    fun showPanel() {
        togglePanel(View.VISIBLE)
        closePanelHandler.removeCallbacks(closePanelRunnable)
        closePanelHandler.postDelayed(closePanelRunnable, PANEL_VISIBILITY_DURATION)
    }

    fun hidePanel() {
        togglePanel(View.GONE)
    }

    private fun togglePanel(visibility: Int) {
        val transition: Transition = Fade()
        if (visibility == View.GONE) {
            transition.duration = 300
        } else {
            transition.duration = 0
        }
        transition.addTarget(R.id.exposure_bar)

        TransitionManager.beginDelayedTransition(
            mainActivity.window.decorView.rootView as ViewGroup, transition
        )

        mainActivity.exposureBarPanel.visibility = visibility
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
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
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                progress = max - (max * event.y / (height / 2)).toInt()

                Log.i("progress", progress.toString())
                Log.i("max", max.toString())

                mainActivity.camConfig.camera?.cameraControl
                    ?.setExposureCompensationIndex(progress)

                showPanel()

                onSizeChanged(width, height, 0, 0)
            }
            MotionEvent.ACTION_CANCEL -> {
            }
        }
        return true
    }
}
