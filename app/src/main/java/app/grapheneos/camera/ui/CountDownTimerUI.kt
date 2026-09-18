package app.grapheneos.camera.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction

class CountDownTimerUI @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    lateinit var mActivity: MainActivity

    companion object {
        private const val textAnimDuration = 700L

        const val startSize = 12f
        const val endSize = 100f
    }

    var isRunning = false
        private set

    /** The mode's own description for the capture button, to put back once the countdown ends. */
    private var captureButtonDescription: CharSequence? = null

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
        onTimerEnd(true)
    }

    private fun beforeTimeStarts() {

        mActivity.settingsIcon.visibility = View.INVISIBLE
        mActivity.thirdOption.visibility = View.INVISIBLE
        mActivity.flipCameraCircle.visibility = View.INVISIBLE
        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.cancelButtonView.visibility = View.INVISIBLE
        mActivity.cbText.visibility = View.INVISIBLE
        mActivity.cbCross.visibility = View.VISIBLE

        // The capture button cancels the countdown while one is up, so it must not keep announcing
        // itself as the shutter. Only the description changes; the cross is drawn over the button.
        captureButtonDescription = mActivity.captureButton.contentDescription
        mActivity.captureButton.contentDescription = mActivity.getString(R.string.cancel_timer)

        visibility = View.VISIBLE
        isRunning = true
    }

    private fun onTimerEnd(isCancelled: Boolean = false) {
        mActivity.settingsIcon.visibility = View.VISIBLE
        mActivity.flipCameraCircle.visibility = View.VISIBLE
        mActivity.cancelButtonView.visibility = View.VISIBLE
        mActivity.cbCross.visibility = View.INVISIBLE

        captureButtonDescription?.let {
            mActivity.captureButton.contentDescription = it
            captureButtonDescription = null
        }

        if (mActivity !is CaptureActivity) {
            mActivity.cbText.visibility = View.VISIBLE
            mActivity.tabLayout.visibility = View.VISIBLE
            mActivity.thirdOption.visibility = View.VISIBLE
        } else if (isCancelled) {
            mActivity.cbText.visibility = View.VISIBLE
        }

        visibility = View.GONE
        isRunning = false
    }

    init {
        gravity = Gravity.CENTER
    }
}
