package app.grapheneos.camera

import android.Manifest
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfTimerRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    /** An activity on a dozing device is already stopped, and cannot be paused again. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * A countdown left running in the background used to keep ticking and fire its capture into a
     * camera that had already been unbound.
     */
    @Test
    fun selfTimer_isCancelledWhenTheActivityPauses() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            // Only a resumed activity can be paused: moveToState below would otherwise be a no-op
            // and the test would pass without exercising the fix.
            assertEquals(Lifecycle.State.RESUMED, scenario.state)

            scenario.onActivity { activity ->
                activity.camConfig.switchMode(CameraMode.CAMERA)
                activity.timerDuration = 10
                activity.cdTimer.startTimer()
                assertTrue(activity.cdTimer.isRunning)
            }

            scenario.moveToState(Lifecycle.State.CREATED)

            scenario.onActivity { activity ->
                assertFalse(activity.cdTimer.isRunning)
                assertEquals(View.GONE, activity.cdTimer.visibility)
                assertEquals(View.INVISIBLE, activity.cbCross.visibility)
            }

            // Closing from a stopped state waits out the whole activity lifecycle timeout: the
            // empty activity the framework stopped us with is already resumed, so the resume it
            // blocks on before finishing us never arrives.
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    /**
     * Cancelling a countdown restores the controls it hid. Doing that with no countdown up would
     * hand them back in a mode that hides them for its own reasons.
     */
    @Test
    fun cancelTimer_withNoCountdownRunning_leavesQrModeControlsHidden() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            // A countdown has to have run at least once for the bug to be reachable: the guard this
            // replaced asked whether a timer had ever been built, not whether one was up.
            scenario.onActivity { activity ->
                activity.timerDuration = 1
                activity.cdTimer.startTimer()
                activity.cdTimer.cancelTimer()
                assertFalse(activity.cdTimer.isRunning)
            }

            scenario.onActivity { it.camConfig.switchMode(CameraMode.QR_SCAN) }
            waitUntil(scenario, "QR mode is active") { it.camConfig.isQRMode }

            scenario.onActivity { activity ->
                activity.cdTimer.cancelTimer()

                assertEquals(View.INVISIBLE, activity.thirdOption.visibility)
                assertEquals(View.INVISIBLE, activity.cancelButtonView.visibility)
                assertEquals(View.INVISIBLE, activity.cbText.visibility)
            }
        }
    }

    /** The badge announces a pending self-timer, so it may only show where one can fire. */
    @Test
    fun selfTimerBadge_tracksThePendingDurationInPhotoMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            scenario.onActivity { activity ->
                activity.camConfig.switchMode(CameraMode.CAMERA)

                activity.timerDuration = 5
                activity.updateSelfTimerBadge()
                assertEquals(View.VISIBLE, activity.cbText.visibility)
                assertEquals("5s", activity.cbText.text.toString())

                activity.timerDuration = 0
                activity.updateSelfTimerBadge()
                assertEquals(View.INVISIBLE, activity.cbText.visibility)
                assertEquals("", activity.cbText.text.toString())
            }
        }
    }

    /**
     * The self-timer is inherited from the last photo session, so a video-only activity can hold a
     * duration it will never use. It must not advertise one.
     */
    @Test
    fun selfTimerBadge_staysHiddenWhereNoPhotoCanBeTaken() {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            scenario.onActivity { activity ->
                activity.timerDuration = 5
                activity.updateSelfTimerBadge()

                assertTrue(activity.camConfig.isVideoMode)
                assertEquals(View.INVISIBLE, activity.cbText.visibility)
            }
        }
    }
}
