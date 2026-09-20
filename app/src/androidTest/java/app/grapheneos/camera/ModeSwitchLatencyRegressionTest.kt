package app.grapheneos.camera

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModeSwitchLatencyRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * Asking whether video, photo and preview can be bound together costs the camera service most
     * of a tenth of a second, and the answer is the same every time the user goes back to video
     * mode.
     */
    @Test
    fun reEnteringVideoMode_reusesTheSnapshotProbeVerdict() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)

            var snapshotsFirstTime = false
            scenario.onActivity {
                CamConfig.clearSnapshotProbeCache()

                it.camConfig.switchMode(CameraMode.VIDEO)

                assertEquals("entering video mode did not probe", 1, CamConfig.snapshotProbeCount)
                snapshotsFirstTime = it.camConfig.imageCapture != null
            }

            scenario.onActivity {
                it.camConfig.switchMode(CameraMode.CAMERA)
                it.camConfig.switchMode(CameraMode.VIDEO)

                assertEquals("the second entry probed again", 1, CamConfig.snapshotProbeCount)
                assertEquals(
                    "the cached verdict answered for a different snapshot decision",
                    snapshotsFirstTime,
                    it.camConfig.imageCapture != null
                )
            }
        }
    }

    /**
     * The verdict above is only reusable for the configuration it was reached with: whether a
     * camera can record at a given quality and take a photo at the same time is exactly the sort
     * of thing that stops being true at another quality. A setting that reaches one of the probed
     * session configurations without reaching the cache key would be answered from a stale
     * verdict.
     */
    @Test
    fun changingTheVideoQuality_reprobesTheSnapshotVerdict() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)

            scenario.onActivity {
                CamConfig.clearSnapshotProbeCache()
                it.camConfig.switchMode(CameraMode.VIDEO)
            }

            // The qualities a camera records at are listed once its preview starts streaming,
            // which is a good while after the mode switch that bound it returns.
            waitUntil(scenario, "the video qualities are listed") {
                it.settingsDialog.videoQualitySpinner.count > 0
            }

            scenario.onActivity { activity ->
                val spinner = activity.settingsDialog.videoQualitySpinner
                assumeTrue(
                    "this camera records at a single quality", spinner.count > 1
                )

                val quality = activity.camConfig.videoQuality
                val original = spinner.selectedItemPosition

                try {
                    selectVideoQuality(activity, if (original == 0) 1 else 0)

                    assertNotEquals(
                        "the video quality did not change",
                        quality,
                        activity.camConfig.videoQuality
                    )
                    assertEquals(
                        "the new quality was answered from the old verdict",
                        2,
                        CamConfig.snapshotProbeCount
                    )
                } finally {
                    selectVideoQuality(activity, original)
                }
            }
        }
    }

    /**
     * Behind a vendor extension, reading the zoom state off the camera costs another tenth of a
     * second, spent with the main thread blocked in the middle of a mode switch. Nothing needs the
     * answer before the switch returns, so it is read from the next message instead.
     */
    @Test
    fun bindingACamera_doesNotReadTheZoomStateOnTheBindPath() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)
            waitUntil(scenario, "the zoom state is attached") { it.camConfig.zoomState != null }

            scenario.onActivity {
                it.camConfig.switchMode(CameraMode.VIDEO)

                assertNull("the bind read the zoom state", it.camConfig.zoomState)
            }

            waitUntil(scenario, "the zoom state is attached again") {
                it.camConfig.zoomState != null
            }
        }
    }

    /**
     * The other side of the deferral: the zoom state has to come back, and it has to describe the
     * camera that was just bound rather than the one it replaced.
     */
    @Test
    fun switchingModes_leavesTheZoomBarUsable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)

            scenario.onActivity { it.camConfig.switchMode(CameraMode.VIDEO) }
            waitUntil(scenario, "the zoom state is attached") { it.camConfig.zoomState != null }

            scenario.onActivity { it.camConfig.camera!!.cameraControl.setLinearZoom(0.5f) }

            waitUntil(scenario, "the zoom bar caught up with the camera") {
                it.zoomBar.progress == 50
            }
            scenario.onActivity {
                assertEquals(0.5f, it.camConfig.zoomState!!.linearZoom, 0.01f)
            }
        }
    }

    // Both halves are needed: the setter behind updateVideoQuality() persists whichever quality
    // the spinner is showing, not the one it is passed.
    private fun selectVideoQuality(activity: MainActivity, position: Int) {
        val dialog = activity.settingsDialog
        dialog.videoQualitySpinner.setSelection(position)
        dialog.updateVideoQuality(dialog.videoQualitySpinner.getItemAtPosition(position) as String)
    }
}
