package app.grapheneos.camera

import android.Manifest
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.BottomTabLayout
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Only a touch committed a mode. A click from an accessibility service -- Switch Access, Voice
 * Access, TalkBack's actions menu -- only selected the tab, and scrolling the strip by action or a
 * mouse wheel, or bringing a clipped tab into view for TalkBack, focus or a key, glided the
 * highlight to a mode the camera never entered, so the shutter went on to take a photo under a
 * strip that said VIDEO.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityActionRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun clickingATab_switchesTheCameraToIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStripAt(scenario, CameraMode.CAMERA)

            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                val video = tabs.getTabForMode(CameraMode.VIDEO)!!.position
                assertTrue("the click was refused",
                    tabViewAt(tabs, video).performAccessibilityAction(ACTION_CLICK, null))
            }

            awaitCameraAndStripIn(scenario, CameraMode.VIDEO)
        }
    }

    @Test
    fun scrollingTheStripForward_switchesTheCameraToTheNextMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStripAt(scenario, CameraMode.CAMERA)

            val target = scrollStrip(scenario, ACTION_SCROLL_FORWARD)

            awaitCameraAndStripIn(scenario, target)
        }
    }

    @Test
    fun scrollingTheStripBackward_switchesTheCameraToThePreviousMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Back from CAMERA lands on whichever extension mode the device has, if any
            awaitStripAt(scenario, CameraMode.VIDEO)

            val target = scrollStrip(scenario, ACTION_SCROLL_BACKWARD)

            awaitCameraAndStripIn(scenario, target)
        }
    }

    @Test
    fun showingAClippedTabOnScreen_leavesTheStripOnTheCamerasMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val clipped = awaitStripAtItsLastMode(scenario)

            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                val scrollX = tabs.scrollX

                clipped.forEach {
                    tabViewAt(tabs, it).performAccessibilityAction(ACTION_SHOW_ON_SCREEN, null)
                }

                assertEquals(scrollX, tabs.scrollX)
                assertEquals(CameraMode.VIDEO, tabs.selectedTab?.tag)
            }
        }
    }

    @Test
    fun focusingAClippedTab_leavesTheStripOnTheCamerasMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val clipped = awaitStripAtItsLastMode(scenario)

            inKeyboardMode(scenario) {
                scenario.onActivity { activity ->
                    val tabs = activity.tabLayout
                    val scrollX = tabs.scrollX

                    clipped.forEach {
                        assertTrue("the tab took no focus", tabViewAt(tabs, it).requestFocus())
                    }

                    assertEquals(scrollX, tabs.scrollX)
                    assertEquals(CameraMode.VIDEO, tabs.selectedTab?.tag)
                }
            }
        }
    }

    @Test
    fun arrowKeysAcrossTheStrip_leaveItOnTheCamerasMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStripAt(scenario, CameraMode.CAMERA)

            inKeyboardMode(scenario) {
                var scrollX = 0
                var tabCount = 0
                scenario.onActivity { activity ->
                    val tabs = activity.tabLayout
                    scrollX = tabs.scrollX
                    tabCount = tabs.tabCount
                    val selected = tabViewAt(tabs, tabs.selectedTabPosition)
                    assertTrue("the tab took no focus", selected.requestFocus())
                }

                // Out to one end of the strip and on to the other
                repeat(tabCount) { instrumentation.sendKeyDownUpSync(KEYCODE_DPAD_LEFT) }
                repeat(2 * tabCount) { instrumentation.sendKeyDownUpSync(KEYCODE_DPAD_RIGHT) }
                // Lets a smooth scroll from a key finish
                SystemClock.sleep(500)

                scenario.onActivity { activity ->
                    assertEquals(scrollX, activity.tabLayout.scrollX)
                    assertEquals(CameraMode.CAMERA, activity.tabLayout.selectedTab?.tag)
                }
            }
        }
    }

    @Test
    fun scrollingAMouseWheelSidewaysOverTheStrip_leavesItOnTheCamerasMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStripAt(scenario, CameraMode.CAMERA)

            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                val scrollX = tabs.scrollX

                val notch = sidewaysWheelNotch(tabs)
                try {
                    tabs.dispatchGenericMotionEvent(notch)
                } finally {
                    notch.recycle()
                }

                assertEquals(scrollX, tabs.scrollX)
                assertEquals(CameraMode.CAMERA, tabs.selectedTab?.tag)
            }
        }
    }

    private fun sidewaysWheelNotch(tabs: View): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = tabs.width / 2f
            y = tabs.height / 2f
            setAxisValue(MotionEvent.AXIS_HSCROLL, 1f)
        }
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(
            now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
        )
    }

    private fun inKeyboardMode(scenario: ActivityScenario<MainActivity>, block: () -> Unit) {
        instrumentation.setInTouchMode(false)
        try {
            waitUntil(scenario, "the window is out of touch mode") {
                !it.window.decorView.isInTouchMode
            }
            block()
        } finally {
            instrumentation.setInTouchMode(true)
        }
    }

    /**
     * Centres the strip on VIDEO, its last tab, which leaves the most tabs clipped, and returns
     * those. A strip wide enough to show them all has nothing to test.
     */
    private fun awaitStripAtItsLastMode(scenario: ActivityScenario<MainActivity>): List<Int> {
        awaitStripAt(scenario, CameraMode.VIDEO)
        lateinit var clipped: List<Int>
        scenario.onActivity { activity ->
            val tabs = activity.tabLayout
            clipped = (0 until tabs.tabCount).filter {
                val tabView = tabViewAt(tabs, it)
                tabView.left < tabs.scrollX || tabView.right > tabs.scrollX + tabs.width
            }
        }
        assumeTrue("the strip clips no tab", clipped.isNotEmpty())
        return clipped
    }

    /**
     * Scrolls the strip by [action] and returns the mode of the tab next to the selected one in
     * the direction the strip scrolls: a forward scroll moves it to the right.
     */
    private fun scrollStrip(scenario: ActivityScenario<MainActivity>, action: Int): CameraMode {
        lateinit var target: CameraMode
        scenario.onActivity { activity ->
            val tabs = activity.tabLayout
            val from = centreOf(tabs, tabs.selectedTabPosition)
            val towards = if (action == ACTION_SCROLL_FORWARD) 1 else -1
            val next = (0 until tabs.tabCount)
                .filter { (centreOf(tabs, it) - from) * towards > 0 }
                .minByOrNull { (centreOf(tabs, it) - from) * towards }
            assertNotNull("the strip has no tab that way to scroll to", next)
            target = tabs.getTabAt(next!!)!!.tag as CameraMode

            assertTrue("the scroll was refused", tabs.performAccessibilityAction(action, null))
        }
        return target
    }

    private fun awaitStripAt(scenario: ActivityScenario<MainActivity>, mode: CameraMode) {
        awaitModeTabs(scenario)
        scenario.onActivity { it.camConfig.switchMode(mode) }
        // Both a scroll still running and a camera not streaming yet would hand the action a
        // strip and a camera on their way somewhere else
        waitUntil(scenario, "the strip is centred on $mode, with the camera streaming") {
            val tabs = it.tabLayout
            it.camConfig.currentMode == mode &&
                tabs.selectedTab?.tag == mode &&
                tabs.width > 0 &&
                tabs.scrollX == centreOf(tabs, tabs.selectedTabPosition) &&
                it.previewView.previewStreamState.value == PreviewView.StreamState.STREAMING
        }
    }

    private fun awaitCameraAndStripIn(scenario: ActivityScenario<MainActivity>, mode: CameraMode) {
        waitUntil(scenario, "the camera is in $mode, which the strip is centred on") {
            val tabs = it.tabLayout
            val position = tabs.getTabForMode(mode)!!.position
            it.camConfig.currentMode == mode &&
                tabViewAt(tabs, position).isSelected &&
                tabs.scrollX == centreOf(tabs, position)
        }
    }

    private fun centreOf(tabs: BottomTabLayout, position: Int): Int {
        val tabView = tabViewAt(tabs, position)
        return tabView.left + tabView.width / 2 - tabs.width / 2
    }

    private fun tabViewAt(tabs: BottomTabLayout, position: Int): View =
        (tabs.getChildAt(0) as ViewGroup).getChildAt(position)

    private companion object {
        private val ACTION_CLICK = AccessibilityAction.ACTION_CLICK.id
        private val ACTION_SCROLL_FORWARD = AccessibilityAction.ACTION_SCROLL_FORWARD.id
        private val ACTION_SCROLL_BACKWARD = AccessibilityAction.ACTION_SCROLL_BACKWARD.id
        private val ACTION_SHOW_ON_SCREEN = AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
    }
}
