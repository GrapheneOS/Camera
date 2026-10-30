package app.grapheneos.camera

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPanelRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * Back used to fall through the panel to the activity, which left the panel on screen and
     * sent the camera to the background.
     */
    @Test
    fun back_dismissesTheSettingsPanel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { cameraSession(it).camera != null }

            scenario.onActivity {
                it.viewfinder.onAction(SettingsAction.QuickSettingsButtonClicked)
            }
            waitUntil(scenario, "the settings panel takes back") { interceptsBack(it) }

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitUntil(scenario, "the settings panel is dismissed") { !interceptsBack(it) }

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * Back belongs to the panel only while the panel is up. A callback left enabled would swallow
     * back for the rest of the session, and one never enabled would not dismiss the panel at all.
     */
    @Test
    fun backIsIntercepted_onlyWhileTheSettingsPanelIsUp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { cameraSession(it).camera != null }

            scenario.onActivity { activity ->
                assertFalse(interceptsBack(activity))

                activity.viewfinder.onAction(SettingsAction.QuickSettingsButtonClicked)
            }
            waitUntil(scenario, "the settings panel takes back") { interceptsBack(it) }

            scenario.onActivity {
                it.viewfinder.onAction(CameraAction.Preview.Tapped(x = 0f, y = 0f))
            }
            waitUntil(scenario, "the settings panel gives back up") { !interceptsBack(it) }
        }
    }

    private fun interceptsBack(activity: MainActivity): Boolean {
        return activity.onBackPressedDispatcher.hasEnabledCallbacks()
    }
}
