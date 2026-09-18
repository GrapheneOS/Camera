package app.grapheneos.camera.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction

class CountDownTimerUI @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    lateinit var mActivity: MainActivity

    var isRunning = false
        private set

    init {
        gravity = Gravity.CENTER
    }

    fun setMainActivity(mainActivity: MainActivity) {
        this.mActivity = mainActivity
    }

    fun startTimer() {
        mActivity.viewfinder.onAction(CaptureAction.SelfTimerStartClicked)
    }

    fun cancelTimer() {
        mActivity.viewfinder.onAction(CaptureAction.SelfTimerCancelClicked)
    }

    fun onTimerStarted() {
        beforeTimeStarts()
    }

    fun onTick(secondsLeft: Int) {
        val scaleAnimation = ValueAnimator.ofFloat(startSize, endSize)
        scaleAnimation.interpolator = AccelerateDecelerateInterpolator()
        scaleAnimation.duration = textAnimDuration

        scaleAnimation.addUpdateListener { valueAnimator ->
            textSize = valueAnimator.animatedValue as Float
        }

        val opacityAnimation = ValueAnimator.ofFloat(1f, 0f)
        opacityAnimation.interpolator = AccelerateDecelerateInterpolator()
        opacityAnimation.duration = textAnimDuration

        opacityAnimation.addUpdateListener { valueAnimator ->
            alpha = valueAnimator.animatedValue as Float
        }

        scaleAnimation.start()
        opacityAnimation.start()

        text = secondsLeft.toString()

        when (secondsLeft) {
            1 -> mActivity.tunePlayer.playTimerFinalSSound()
            else -> mActivity.tunePlayer.playTimerIncrementSound()
        }
    }

    fun onTimerFinished() {
        onTimerEnd()
        when (mActivity) {
            is CaptureActivity -> (mActivity as CaptureActivity).takePicture()
            else -> mActivity.imageCapturer.takePicture()
        }
    }

    fun onTimerCancelled() {
        onTimerEnd()
    }

    private fun beforeTimeStarts() {
        mActivity.settingsIcon.visibility = View.INVISIBLE
        mActivity.flipCameraCircle.visibility = View.INVISIBLE
        mActivity.tabLayout.visibility = View.INVISIBLE

        isRunning = true
    }

    private fun onTimerEnd() {
        mActivity.settingsIcon.visibility = View.VISIBLE
        mActivity.flipCameraCircle.visibility = View.VISIBLE

        if (mActivity !is CaptureActivity) {
            mActivity.tabLayout.visibility = View.VISIBLE
        }

        isRunning = false
    }

    companion object {
        private const val textAnimDuration = 700L

        const val startSize = 12f
        const val endSize = 100f
    }
}
