package app.grapheneos.camera

import android.Manifest
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.BottomTabLayout
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Only a touch committed a mode. A click from an accessibility service -- Switch Access, Voice
 * Access, TalkBack's actions menu -- only selected the tab, so the shutter went on to take a photo
 * under a strip that said VIDEO.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityActionRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

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
    }
}
