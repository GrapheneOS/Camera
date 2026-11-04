package app.grapheneos.camera.ui.camerascreen

import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class HiddenStatusBar(
    window: Window,
    private val view: View,
) {

    private val controller = WindowInsetsControllerCompat(window, view)
    private val hideListener = HideListener()
    private var hasControl = false
    private var isHiding = false

    fun install() {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.hide(STATUS_BARS)
        controller.addOnControllableInsetsChangedListener { _, typeMask ->
            hasControl = typeMask and STATUS_BARS != 0
        }
        ViewCompat.setWindowInsetsAnimationCallback(view, AnimationCallback())
    }

    fun onWindowInsetsApplied(insets: WindowInsetsCompat) {
        if (insets.isVisible(STATUS_BARS)) {
            hideWithoutAnimation()
        }
    }

    // A request made without control is neither started nor cancelled until control arrives, and
    // would hold back the one made when it does.
    private fun hideWithoutAnimation() {
        when {
            isHiding -> Unit
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> controller.hide(STATUS_BARS)
            hasControl -> {
                isHiding = true
                controller.controlWindowInsetsAnimation(
                    STATUS_BARS,
                    0,
                    null,
                    null,
                    hideListener,
                )
            }
        }
    }

    // On gaining control the system hides the status bar with an animation of its own. Taking
    // control of one that is already hidden would show it for the length of the control, so only
    // such an animation is taken over.
    private inner class AnimationCallback :
        WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {

        override fun onPrepare(animation: WindowInsetsAnimationCompat) {
            if (animation.typeMask and STATUS_BARS != 0 && !isHiding) {
                view.post { hideWithoutAnimation() }
            }
        }

        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: MutableList<WindowInsetsAnimationCompat>,
        ): WindowInsetsCompat {
            return insets
        }
    }

    private inner class HideListener : WindowInsetsAnimationControlListenerCompat {

        override fun onReady(controller: WindowInsetsAnimationControllerCompat, types: Int) {
            controller.finish(false)
        }

        override fun onFinished(controller: WindowInsetsAnimationControllerCompat) {
            isHiding = false
        }

        override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
            isHiding = false
        }
    }

    private companion object {
        val STATUS_BARS = WindowInsetsCompat.Type.statusBars()
    }
}
