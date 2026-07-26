package app.grapheneos.camera

import android.os.SystemClock
import android.view.MotionEvent
import app.grapheneos.camera.ui.activities.MainActivity

private const val STEPS = 6
private const val STEP_MS = 10L
private const val STEP_PX = 80f
private const val START_X = STEPS * STEP_PX

/**
 * Flings [activity]'s preview leftwards, the direction that reaches onSwipeLeft(). The swipe
 * handlers are private and onFling() only fires above a velocity threshold, so feeding the
 * activity's own detector a synthetic gesture is the way in. Call on the main thread.
 */
fun flingLeft(activity: MainActivity) {
    val downTime = SystemClock.uptimeMillis()
    val events = ArrayList<MotionEvent>(STEPS + 1)

    // Uniform motion: every sample covers the same distance in the same time, so the detector
    // measures one constant velocity, well clear of both its own threshold and onFling()'s.
    events += MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, START_X, 0f, 0)
    for (step in 1..STEPS) {
        val action = if (step == STEPS) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
        events += MotionEvent.obtain(
            downTime, downTime + step * STEP_MS, action, START_X - step * STEP_PX, 0f, 0
        )
    }

    try {
        events.forEach { activity.gestureDetector.onTouchEvent(it) }
    } finally {
        events.forEach { it.recycle() }
    }
}
