package app.grapheneos.camera.ui.viewfinder.components

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import kotlin.time.Duration

class CountDownTimerUI @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {

    private var renderedRemaining: Duration? = null

    init {
        gravity = Gravity.CENTER
    }

    fun render(remaining: Duration?) {
        if (remaining == renderedRemaining) return
        renderedRemaining = remaining

        if (remaining == null) return

        onTick(remaining.inWholeSeconds)
    }

    private fun onTick(secondsLeft: Long) {
        val scaleAnimation = ValueAnimator.ofFloat(START_SIZE, END_SIZE)
        scaleAnimation.interpolator = AccelerateDecelerateInterpolator()
        scaleAnimation.duration = TEXT_ANIMATION_DURATION

        scaleAnimation.addUpdateListener { valueAnimator ->
            textSize = valueAnimator.animatedValue as Float
        }

        val opacityAnimation = ValueAnimator.ofFloat(1f, 0f)
        opacityAnimation.interpolator = AccelerateDecelerateInterpolator()
        opacityAnimation.duration = TEXT_ANIMATION_DURATION

        opacityAnimation.addUpdateListener { valueAnimator ->
            alpha = valueAnimator.animatedValue as Float
        }

        scaleAnimation.start()
        opacityAnimation.start()

        text = secondsLeft.toString()
    }

    private companion object {
        private const val TEXT_ANIMATION_DURATION = 700L
        private const val START_SIZE = 12f
        private const val END_SIZE = 100f
    }
}
