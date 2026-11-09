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
import kotlin.math.hypot

internal class ViewfinderGestureHandler(
    context: Context,
    private val onAction: (ViewfinderAction) -> Unit,
    private val previewFocus: PreviewFocusCallbacks,
    private val previewZoom: PreviewZoomCallbacks,
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

    private val pinchTracker = PreviewPinchTracker(
        spanSlop = ViewConfiguration.get(context).scaledTouchSlop * 2f,
    )

    private var velocityTracker: VelocityTracker? = null

    private var pointerCount = 0

    private var isZooming = false

    private var wasSwiping = false

    private var pendingTap: PendingTap? = null

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                wasSwiping = false
                isZooming = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> cancelTap()
            MotionEvent.ACTION_CANCEL -> cancelTap()
        }

        pointerCount = event.pointerCount
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        trackSettingsPanelDrag(event)
        trackPinch(event)

        if (event.action != MotionEvent.ACTION_UP) return true

        if (wasSwiping) {
            wasSwiping = false
            return false
        }

        if (isZooming) {
            isZooming = false
            return true
        }

        when (pendingTap) {
            PendingTap.WITH_FOCUS -> {
                onAction(CameraAction.Preview.Tapped)
                previewFocus.onTapped()
            }

            PendingTap.WITHOUT_FOCUS -> onAction(CameraAction.Preview.Tapped)
            null -> Unit
        }

        return v.performClick()
    }

    override fun onDown(event: MotionEvent): Boolean {
        pendingTap = when {
            settingsPanelDrag.isShown() -> PendingTap.WITHOUT_FOCUS
            else -> PendingTap.WITH_FOCUS
        }

        if (pendingTap == PendingTap.WITH_FOCUS) {
            previewFocus.onPressed(event.x, event.y)
        }

        return true
    }

    override fun onLongPress(event: MotionEvent) {
        if (pendingTap == PendingTap.WITH_FOCUS) {
            onAction(CameraAction.Preview.Tapped)
            previewFocus.onLongPressed()
        }

        pendingTap = null
    }

    override fun onScroll(
        first: MotionEvent?,
        current: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        cancelTap()

        return false
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        isZooming = true

        previewZoom.onPinchScaleChanged(detector.scaleFactor)

        return true
    }

    // The detector is left with the double tap held and dragged; pinchTracker follows two fingers.
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return pointerCount == 1 && startZoom()
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        previewZoom.onPinchFinished()
    }

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

    private fun startZoom(): Boolean {
        val starts = !settingsPanelDrag.isShown()

        if (starts) {
            cancelTap()
            stopSettingsPanelDrag(velocity = 0f)
            previewZoom.onPinchStarted()
        }

        return starts
    }

    private fun trackPinch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> pinchTracker.restart(
                span = pointerSpan(
                    event = event,
                    liftedIndex = NO_POINTER,
                ),
            )

            MotionEvent.ACTION_POINTER_UP -> when {
                event.pointerCount > 2 -> pinchTracker.restart(
                    span = pointerSpan(
                        event = event,
                        liftedIndex = event.actionIndex,
                    ),
                )

                else -> stopPinch()
            }

            MotionEvent.ACTION_MOVE -> if (event.pointerCount > 1) movePinch(event)
            MotionEvent.ACTION_UP -> stopPinch()
            MotionEvent.ACTION_CANCEL -> stopPinch()
        }
    }

    private fun movePinch(event: MotionEvent) {
        val span = pointerSpan(
            event = event,
            liftedIndex = NO_POINTER,
        )

        when (val pinch = pinchTracker.move(span)) {
            null -> Unit
            is PreviewPinchTracker.Pinch.Started -> if (!startZoom()) pinchTracker.reject()

            is PreviewPinchTracker.Pinch.Scaled -> {
                isZooming = true
                previewZoom.onPinchScaleChanged(pinch.scaleFactor)
            }
        }
    }

    private fun stopPinch() {
        if (pinchTracker.stop()) previewZoom.onPinchFinished()
    }

    private fun pointerSpan(
        event: MotionEvent,
        liftedIndex: Int,
    ): Float {
        var count = 0
        var sumX = 0f
        var sumY = 0f
        for (index in 0 until event.pointerCount) {
            if (index != liftedIndex) {
                count++
                sumX += event.getX(index)
                sumY += event.getY(index)
            }
        }

        var deviationX = 0f
        var deviationY = 0f
        for (index in 0 until event.pointerCount) {
            if (index != liftedIndex) {
                deviationX += abs(event.getX(index) - sumX / count)
                deviationY += abs(event.getY(index) - sumY / count)
            }
        }

        return hypot(deviationX, deviationY) * 2 / count
    }

    private fun cancelTap() {
        if (pendingTap == PendingTap.WITH_FOCUS) {
            previewFocus.onGestureStarted()
        }

        pendingTap = null
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

    private enum class PendingTap {
        WITH_FOCUS,
        WITHOUT_FOCUS,
    }

    private companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
        private const val PIXELS_PER_SECOND = 1000
        private const val NO_POINTER = -1
    }
}
