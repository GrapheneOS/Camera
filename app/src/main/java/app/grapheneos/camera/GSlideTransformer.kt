package app.grapheneos.camera

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class GSlideTransformer : ViewPager2.PageTransformer {

    companion object {
        private const val MIN_SCALE = 0.75f
    }

    override fun transformPage(view: View, position: Float) {
        if (position < -1f) return
        view.apply {
            val pageWidth = width
            when {
                position <= 0 -> {
                    // Default transition for the left page.
                    alpha = 1f
                    translationX = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
                position <= 1 -> {
                    // Fade the page out.
                    alpha = 1 - position

                    // Offset the built-in slide translation.
                    translationX = pageWidth * -position

                    // Scale the page from full size to MIN_SCALE.
                    val scaleFactor = (MIN_SCALE + (1 - MIN_SCALE) * (1 - abs(position)))
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                }
                else -> {
                    // Hide pages beyond the right edge.
                    alpha = 0f
                }
            }
        }
    }
}