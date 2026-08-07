package app.grapheneos.camera

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ActivityScenario
import app.grapheneos.camera.ui.activities.MainActivity
import kotlin.math.roundToInt

private const val DEFAULT_STEPS = 13
private const val DEFAULT_STEP_MS = 16L

/** Whether a synthetic drag ends with the finger lifted, or with the gesture taken away. */
enum class DragEnd { UP, CANCEL }

/**
 * Drags [scenario]'s mode strip until it has scrolled [scrollPx], and returns every value scrollX
 * took along the way. One event per main thread round trip, with a real pause between them, so the
 * animators a drag must not start get their Choreographer frames -- pushed through in one block
 * they would never run and a strip that fights the finger would look still.
 *
 * [onStep] runs on the main thread right after each move, [onLift] in the same main thread block as
 * the gesture's last event. Anything that has to be seen before the mode switch belongs in
 * [onLift]: the switch blocks the main thread for half a second, and a round trip made after this
 * function returns queues up behind it. Call from the test thread.
 */
fun <A : MainActivity> dragStrip(
    scenario: ActivityScenario<A>,
    scrollPx: Int,
    steps: Int = DEFAULT_STEPS,
    stepMs: Long = DEFAULT_STEP_MS,
    endWith: DragEnd = DragEnd.UP,
    onStep: (A) -> Unit = {},
    onLift: (A) -> Unit = {},
): List<Int> {
    require(steps > 0) { "a drag needs at least one move" }
    require(scrollPx != 0) { "a drag that asks for no scroll would prove nothing" }

    val trace = ArrayList<Int>()
    val events = ArrayList<MotionEvent>(steps + 3)
    val downTime = SystemClock.uptimeMillis()
    var eventTime = downTime

    // Through the activity rather than straight at the strip: a mode switch takes work off the
    // activity's own dispatch, and events handed to the strip would skip it.
    fun dispatch(action: Int, x: Float, y: Float, then: (A) -> Unit = {}) {
        eventTime = maxOf(eventTime + 1, SystemClock.uptimeMillis())
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        events += event
        scenario.onActivity { activity ->
            activity.dispatchTouchEvent(event)
            then(activity)
        }
    }

    var startX = 0f
    var y = 0f
    var slopPx = 0f
    scenario.onActivity { activity ->
        val tabs = activity.tabLayout
        val origin = IntArray(2)
        tabs.getLocationInWindow(origin)
        startX = (origin[0] + tabs.width / 2).toFloat()
        y = (origin[1] + tabs.height / 2).toFloat()
        slopPx = (ViewConfiguration.get(activity).scaledTouchSlop + 1).toFloat()
        trace += tabs.scrollX
        tabs.setOnScrollChangeListener { _, scrollX, _, _, _ -> trace += scrollX }
    }

    // The strip scrolls against the finger, and the slop the scroll view swallows before it starts
    // has to come out of the same direction as the travel.
    val fingerTravel = -scrollPx.toFloat()
    val slopBreak = if (scrollPx > 0) -slopPx else slopPx

    var sampled: List<Int> = emptyList()
    try {
        dispatch(MotionEvent.ACTION_DOWN, startX, y)
        val slopX = startX + slopBreak
        dispatch(MotionEvent.ACTION_MOVE, slopX, y)

        for (step in 1..steps) {
            Thread.sleep(stepMs)
            dispatch(
                MotionEvent.ACTION_MOVE,
                slopX + (fingerTravel * step / steps).roundToInt(),
                y,
            ) { onStep(it) }
        }

        val end = when (endWith) {
            DragEnd.UP -> MotionEvent.ACTION_UP
            DragEnd.CANCEL -> MotionEvent.ACTION_CANCEL
        }

        // Stop listening in the same main thread block that ends the gesture: the settle animation
        // it starts would otherwise land a frame or two in the trace and read as the finger's.
        dispatch(end, slopX + fingerTravel, y) { activity ->
            activity.tabLayout.setOnScrollChangeListener(null)
            sampled = ArrayList(trace)
            onLift(activity)
        }
    } finally {
        scenario.onActivity { activity ->
            activity.tabLayout.setOnScrollChangeListener(null)
            events.forEach { it.recycle() }
        }
    }
    return sampled
}
