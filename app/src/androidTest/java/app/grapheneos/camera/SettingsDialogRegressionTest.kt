package app.grapheneos.camera

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDialogRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    /** The panel dismisses itself from an animation callback, which needs a visible window. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * The panel's window is not focusable, so back never reached it: the event fell through to the
     * activity, which left the panel on screen and sent the camera to the background.
     */
    @Test
    fun back_dismissesTheSettingsPanel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            scenario.onActivity { activity ->
                activity.settingsDialog.show()
                assertTrue(activity.settingsDialog.isShowing)
            }

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            // The panel dismisses itself at the end of the slide-up animation
            waitUntil(scenario, "the settings panel is dismissed") {
                !it.settingsDialog.isShowing
            }

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
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            scenario.onActivity { activity ->
                assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())

                activity.settingsDialog.show()
                assertTrue(activity.onBackPressedDispatcher.hasEnabledCallbacks())

                activity.settingsDialog.slideDialogUp()
            }

            waitUntil(scenario, "the settings panel is dismissed") {
                !it.settingsDialog.isShowing
            }

            scenario.onActivity {
                assertFalse(it.onBackPressedDispatcher.hasEnabledCallbacks())
            }
        }
    }
}
