package app.grapheneos.camera.ui.viewfinder

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.camera.core.FocusMeteringAction
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal class ViewfinderGestureHandler(
    private val activity: MainActivity,
) : View.OnTouchListener,
    ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    val gestureDetector = GestureDetector(activity, this)

    private val scaleGestureDetector = ScaleGestureDetector(activity, this)

    private var isZooming = false

    private var wasSwiping = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.action != MotionEvent.ACTION_UP) return true

        if (wasSwiping) {
            wasSwiping = false
            return false
        }

        if (isZooming) {
            isZooming = false
            return true
        }

        if (activity.camConfig.isQRMode) {
            return false
        }

        val x = event.x
        val y = event.y

        val camera = activity.session.camera ?: return true

        val autoFocusPoint = activity.previewView.meteringPointFactory.createPoint(x, y)
        activity.animateFocusRing(x, y)

        val focusBuilder = FocusMeteringAction.Builder(autoFocusPoint)

        if (!activity.camConfig.isVideoMode) {
            activity.camConfig.mPlayer.playFocusStartSound()
        }

        if (activity.camConfig.focusTimeout == 0L) {
            focusBuilder.disableAutoCancel()
        } else {
            focusBuilder.setAutoCancelDuration(
                activity.camConfig.focusTimeout,
                TimeUnit.SECONDS,
            )
        }

        camera.cameraControl.startFocusAndMetering(focusBuilder.build())

        activity.exposureBar.showPanel()
        activity.zoomBar.showPanel()

        return v.performClick()
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        isZooming = true

        val zoomState = activity.session.zoomState
        var scale = 1f

        if (zoomState != null) {
            scale = zoomState.zoomRatio * detector.scaleFactor
        }

        val camera = activity.session.camera ?: return true
        camera.cameraControl.setZoomRatio(scale)

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        return false
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        e1 ?: return false

        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y

        return try {
            when {
                abs(diffX) > abs(diffY) -> onHorizontalFling(diffX, velocityX)
                else -> onVerticalFling(diffY, velocityY)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            false
        }
    }

    private fun onHorizontalFling(distance: Float, velocity: Float): Boolean {
        if (!isSwipe(distance, velocity)) return false

        when {
            distance > 0 -> onSwipeRight()
            else -> onSwipeLeft()
        }

        return true
    }

    private fun onVerticalFling(distance: Float, velocity: Float): Boolean {
        if (!isSwipe(distance, velocity)) return false

        when {
            distance > 0 -> onSwipeBottom()
            else -> onSwipeTop()
        }

        return true
    }

    private fun isSwipe(distance: Float, velocity: Float): Boolean {
        return abs(distance) > SWIPE_THRESHOLD && abs(velocity) > SWIPE_VELOCITY_THRESHOLD
    }

    private fun onSwipeBottom() {
        if (isZooming || activity.cdTimer.isRunning) return

        wasSwiping = true

        if (activity.settingsDialog.isShowing) return

        when {
            !activity.camConfig.isQRMode -> {
                if (activity.settingsIcon.isEnabled) {
                    activity.settingsIcon.performClick()
                }
            }

            !activity.camConfig.scanAllCodes -> {
                activity.camConfig.showMoreOptionsForQR()
            }
        }
    }

    private fun onSwipeRight() {
        if (isZooming || activity.cdTimer.isRunning || activity.videoCapturer.isRecording) return

        if (activity is VideoOnlyActivity) return

        wasSwiping = true

        if (activity.settingsDialog.isShowing) return

        val i = activity.tabLayout.selectedTabPosition - 1

        Log.i(TAG, "onSwipeRight $i")

        activity.tabLayout.getTabAt(i)?.let {
            activity.finalizeMode(it)
        }
    }

    private fun onSwipeTop() {
        if (isZooming || activity.cdTimer.isRunning || activity.videoCapturer.isRecording) return

        wasSwiping = true
        activity.settingsDialog.slideDialogUp()
    }

    private fun onSwipeLeft() {
        if (isZooming || activity.cdTimer.isRunning || activity.videoCapturer.isRecording) return

        if (activity is VideoOnlyActivity) return

        wasSwiping = true

        if (activity.settingsDialog.isShowing) return

        val i = activity.tabLayout.selectedTabPosition + 1
        activity.tabLayout.getTabAt(i)?.let {
            activity.finalizeMode(it)
        }
    }

    override fun onSingleTapConfirmed(p0: MotionEvent): Boolean {
        return false
    }

    override fun onDoubleTap(p0: MotionEvent): Boolean {
        return false
    }

    override fun onDoubleTapEvent(p0: MotionEvent): Boolean {
        return false
    }

    private companion object {
        private const val TAG = "ViewfinderGesture"
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
