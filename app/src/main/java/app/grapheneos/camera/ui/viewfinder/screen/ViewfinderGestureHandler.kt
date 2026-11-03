package app.grapheneos.camera.ui.viewfinder.screen

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeDirection
import kotlin.math.abs

internal class ViewfinderGestureHandler(
    context: Context,
    private val onAction: (ViewfinderAction) -> Unit,
    private val settingsPanelDrag: SettingsPanelDragCallbacks,
) : GestureDetector.SimpleOnGestureListener(),
    View.OnTouchListener,
    ScaleGestureDetector.OnScaleGestureListener {

    val gestureDetector = GestureDetector(context, this)

    var isSettingsPanelAvailable = false

    private val scaleGestureDetector = ScaleGestureDetector(context, this)

    private val dragTracker = PreviewDragTracker(
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
    )

    private var velocityTracker: VelocityTracker? = null

    private var isZooming = false

    private var wasSwiping = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            wasSwiping = false
            isZooming = false
        }

        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        trackSettingsPanelDrag(event)

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

    // A pinch, or a double tap held and dragged, zooms only while the panel is down, and stops the
    // panel from following the same finger.
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        if (settingsPanelDrag.isShown()) return false

        stopSettingsPanelDrag(velocity = 0f)

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
            e1 == null || isZooming || dragTracker.isDraggingVertically -> null

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

    private fun trackSettingsPanelDrag(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                dragTracker.down(
                    x = event.x,
                    y = event.y,
                    canDragVertically = isSettingsPanelAvailable,
                )
            }

            MotionEvent.ACTION_MOVE -> moveSettingsPanel(event)
            MotionEvent.ACTION_POINTER_DOWN -> stopSettingsPanelDrag(velocity = 0f)
            MotionEvent.ACTION_UP -> stopSettingsPanelDrag(velocity = releaseVelocity(event))
            MotionEvent.ACTION_CANCEL -> stopSettingsPanelDrag(velocity = 0f)
        }
    }

    private fun moveSettingsPanel(event: MotionEvent) {
        velocityTracker?.addMovement(event)

        when (val drag = dragTracker.move(x = event.x, y = event.y)) {
            null -> Unit

            is PreviewDragTracker.VerticalDrag.Started -> {
                settingsPanelDrag.onDragStarted()
                settingsPanelDrag.onDragged(drag.delta)
            }

            is PreviewDragTracker.VerticalDrag.Moved -> {
                settingsPanelDrag.onDragged(drag.delta)
            }
        }
    }

    private fun releaseVelocity(event: MotionEvent): Float {
        val tracker = velocityTracker ?: return 0f

        tracker.addMovement(event)
        tracker.computeCurrentVelocity(PIXELS_PER_SECOND)

        return tracker.yVelocity
    }

    private fun stopSettingsPanelDrag(velocity: Float) {
        velocityTracker?.recycle()
        velocityTracker = null

        if (dragTracker.stop()) {
            wasSwiping = true
            settingsPanelDrag.onDragStopped(velocity)
        }
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
        private const val PIXELS_PER_SECOND = 1000
    }
}
