package app.grapheneos.camera

import android.Manifest
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tapped tab is highlighted at once, but the camera only switches once the strip has glided over
 * to it, and a touch landing on the strip in that window threw the switch away. That is harmless
 * when the touch goes on to commit a mode of its own, but not when it never does -- cancelled
 * under a palm or a system gesture, or still down when the shutter goes -- and the strip was left
 * showing a mode the camera never entered.
 */
@RunWith(AndroidJUnit4::class)
class TouchDuringTabGlideRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    @Test
    fun tappedMode_whenTheNextTouchIsCancelled_reachesTheCamera() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val target = tapATabThen(scenario) { activity, at ->
                val downTime = SystemClock.uptimeMillis()
                dispatch(activity, downTime, MotionEvent.ACTION_DOWN, at)
                dispatch(activity, downTime, MotionEvent.ACTION_CANCEL, at)
            }

            waitUntil(scenario, "the camera is in $target, which the strip shows") {
                it.camConfig.currentMode == target && it.tabLayout.selectedTab?.tag == target
            }
        }
    }

    @Test
    fun tappedMode_whileTheNextTouchIsDown_reachesTheCameraBeforeTheShutter() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tapATabThen(scenario) { activity, at ->
                val downTime = SystemClock.uptimeMillis()
                dispatch(activity, downTime, MotionEvent.ACTION_DOWN, at)
                try {
                    // What the shutter does before it looks at the mode
                    activity.tabLayout.settleNow()

                    assertEquals(
                        "the shutter would act in the mode the strip has left",
                        activity.tabLayout.selectedTab?.tag,
                        activity.camConfig.currentMode,
                    )
                } finally {
                    dispatch(activity, downTime, MotionEvent.ACTION_CANCEL, at)
                }
            }
        }
    }

    /**
     * Taps the tab next to the selected one, then hands [nextTouch] the spot it tapped while the
     * strip is still gliding over to it, and returns the mode tapped.
     */
    private fun tapATabThen(
        scenario: ActivityScenario<MainActivity>,
        nextTouch: (MainActivity, FloatArray) -> Unit,
    ): CameraMode {
        assumeTrue("a tap switches the camera at once with animations off",
            ValueAnimator.areAnimatorsEnabled())

        awaitModeTabs(scenario)
        // A strip still gliding onto its first tab would leave the tap little or no glide of its own
        waitUntil(scenario, "the strip is centred on the selected tab") {
            val tabs = it.tabLayout
            tabs.width > 0 && tabs.scrollX == centreOf(it, tabs.selectedTabPosition)
        }

        lateinit var start: CameraMode
        lateinit var target: CameraMode
        val at = FloatArray(2)
        scenario.onActivity { activity ->
            val tabs = activity.tabLayout
            val position = tabs.selectedTabPosition
            val next = if (position + 1 < tabs.tabCount) position + 1 else position - 1
            start = activity.camConfig.currentMode
            target = tabs.getTabAt(next)!!.tag as CameraMode

            val tab = tabViewAt(activity, next)
            val origin = IntArray(2)
            tab.getLocationInWindow(origin)
            at[0] = origin[0] + tab.width / 2f
            at[1] = origin[1] + tab.height / 2f

            val downTime = SystemClock.uptimeMillis()
            dispatch(activity, downTime, MotionEvent.ACTION_DOWN, at)
            dispatch(activity, downTime, MotionEvent.ACTION_UP, at)
        }

        scenario.onActivity { activity ->
            assertEquals("the tap did not highlight its tab", target,
                activity.tabLayout.selectedTab?.tag)
            assertEquals("the camera switched before the next touch could land in the glide",
                start, activity.camConfig.currentMode)

            nextTouch(activity, at)
        }
        return target
    }

    // Through the activity rather than straight at the strip, as a real touch reaches it
    private fun dispatch(activity: MainActivity, downTime: Long, action: Int, at: FloatArray) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at[0], at[1], 0)
        try {
            activity.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun centreOf(activity: MainActivity, position: Int): Int {
        val tab = tabViewAt(activity, position)
        return tab.left + tab.width / 2 - activity.tabLayout.width / 2
    }

    private fun tabViewAt(activity: MainActivity, position: Int) =
        (activity.tabLayout.getChildAt(0) as ViewGroup).getChildAt(position)
}
