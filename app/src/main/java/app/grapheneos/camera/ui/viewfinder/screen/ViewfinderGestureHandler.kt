package app.grapheneos.camera.ui.viewfinder.screen

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import kotlin.math.abs

internal class ViewfinderGestureHandler(
    context: Context,
    private val onAction: (ViewfinderAction) -> Unit,
) : GestureDetector.SimpleOnGestureListener(),
    View.OnTouchListener,
    ScaleGestureDetector.OnScaleGestureListener {

    val gestureDetector = GestureDetector(context, this)

    private val scaleGestureDetector = ScaleGestureDetector(context, this)

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

        onAction(CameraAction.Preview.Tapped(x = event.x, y = event.y))

        return v.performClick()
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        isZooming = true

        onAction(CameraAction.Preview.Pinched(scaleFactor = detector.scaleFactor))

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        val direction = when {
            e1 == null || isZooming -> null

            else -> swipeDirection(
                distanceX = e2.x - e1.x,
                distanceY = e2.y - e1.y,
                velocityX = velocityX,
                velocityY = velocityY,
            )
        }

        if (direction != null) {
            wasSwiping = true
            onAction(CameraAction.Preview.Swiped(direction = direction))
        }

        return direction != null
    }

    private fun swipeDirection(
        distanceX: Float,
        distanceY: Float,
        velocityX: Float,
        velocityY: Float,
    ): SwipeDirection? {
        val isHorizontal = abs(distanceX) > abs(distanceY)

        return when {
            isHorizontal && !isSwipe(distanceX, velocityX) -> null
            isHorizontal && distanceX > 0 -> SwipeDirection.RIGHT
            isHorizontal -> SwipeDirection.LEFT
            !isSwipe(distanceY, velocityY) -> null
            distanceY > 0 -> SwipeDirection.DOWN
            else -> SwipeDirection.UP
        }
    }

    private fun isSwipe(
        distance: Float,
        velocity: Float,
    ): Boolean {
        return abs(distance) > SWIPE_THRESHOLD && abs(velocity) > SWIPE_VELOCITY_THRESHOLD
    }

    private companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
