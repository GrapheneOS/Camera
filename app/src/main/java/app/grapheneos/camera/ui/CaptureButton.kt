package app.grapheneos.camera.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageButton
import app.grapheneos.camera.ui.viewfinder.screen.model.CaptureButtonUiState

class CaptureButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageButton(context, attrs) {

    private var rendered: CaptureButtonUiState? = null

    fun render(state: CaptureButtonUiState) {
        val previous = rendered
        rendered = state

        setBackgroundResource(state.background)

        // The drawable must stay the same one the recording's corner-radius animation is holding
        // on to: replacing it, even with the same resource, would cut that animation short.
        if (state.icon != previous?.icon) {
            setImageResource(state.icon)
        }

        contentDescription = context.getString(state.description)

        if (state.enabled != previous?.enabled) {
            renderEnabled(state.enabled)
        }

        if (previous != null && state.recording != previous.recording) {
            animateCorners(recording = state.recording)
        }
    }

    private fun renderEnabled(enabled: Boolean) {
        isEnabled = enabled

        val targetAlpha = when {
            enabled -> ENABLED_ALPHA
            else -> DISABLED_ALPHA
        }
        val animation = AlphaAnimation(alpha, targetAlpha)
        animation.duration = FADE_DURATION
        animation.interpolator = LinearInterpolator()
        animation.fillAfter = true

        startAnimation(animation)
    }

    // If no shape can be dug out, skip the cosmetic animation rather than crash
    private fun animateCorners(recording: Boolean) {
        val shape = findGradientDrawable(drawable) ?: return
        val density = resources.displayMetrics.density
        val corners = when {
            recording -> IDLE_CORNER_DP to RECORDING_CORNER_DP
            else -> RECORDING_CORNER_DP to IDLE_CORNER_DP
        }

        val animator = ValueAnimator.ofFloat(
            corners.first * density,
            corners.second * density,
        )
        animator.setDuration(CORNER_DURATION)
            .addUpdateListener { animation ->
                shape.cornerRadius = animation.animatedValue as Float
            }
        animator.start()
    }

    // Skinned devices wrap the capture button shape in selectors and layer-lists
    private fun findGradientDrawable(drawable: Drawable?): GradientDrawable? {
        return when (drawable) {
            is GradientDrawable -> drawable
            is StateListDrawable -> findGradientDrawable(drawable.current)

            is LayerDrawable -> {
                (0 until drawable.numberOfLayers)
                    .firstNotNullOfOrNull { findGradientDrawable(drawable.getDrawable(it)) }
            }

            else -> null
        }
    }

    private companion object {
        private const val FADE_DURATION = 200L
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.6f
        private const val CORNER_DURATION = 300L
        private const val IDLE_CORNER_DP = 16f
        private const val RECORDING_CORNER_DP = 8f
    }
}
