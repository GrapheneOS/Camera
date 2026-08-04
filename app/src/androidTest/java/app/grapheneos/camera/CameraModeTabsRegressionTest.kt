package app.grapheneos.camera

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import android.view.ViewTreeObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CameraModeTabsRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * The tab strip used to highlight whatever the user last touched, so a mode changed from code
     * (a failed extension bind, the QR tile) left it pointing at another mode.
     */
    @Test
    fun tabStrip_followsAModeChangedFromCode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)

            scenario.onActivity { activity ->
                activity.camConfig.switchMode(CameraMode.VIDEO)
                assertEquals(CameraMode.VIDEO, activity.tabLayout.selectedTab?.tag)

                activity.camConfig.switchMode(CameraMode.CAMERA)
                assertEquals(CameraMode.CAMERA, activity.tabLayout.selectedTab?.tag)
            }
        }
    }

    /**
     * Moving the highlight from within onLayout() made every pass request the next one, pinning the
     * main thread at 100% for as long as the activity lived.
     */
    @Test
    fun tabStrip_settlesAfterAModeChange() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)

            scenario.onActivity { it.camConfig.switchMode(CameraMode.VIDEO) }

            // Counting passes rather than reading isLayoutRequested: a request issued from inside a
            // layout pass is parked for the next traversal and the flag is cleared on the way, so it
            // reads false between frames -- exactly where a sample would land.
            val passes = AtomicInteger()
            val listener = ViewTreeObserver.OnGlobalLayoutListener { passes.incrementAndGet() }
            scenario.onActivity { it.tabLayout.viewTreeObserver.addOnGlobalLayoutListener(listener) }
            Thread.sleep(WINDOW_MS)
            scenario.onActivity {
                it.tabLayout.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }

            // Rebinding the camera behind the mode change costs one pass. The livelock cost one per
            // frame, so anything near this window's 30-60 frames is the regression coming back.
            val observed = passes.get()
            assertTrue("The tab strip was laid out $observed times in ${WINDOW_MS}ms", observed < 5)
        }
    }

    /**
     * The strip was only made transparent there, and a transparent strip still takes taps: a
     * disabled parent does not disable its children.
     */
    @Test
    fun videoOnlyActivity_buildsNoModeTabs() {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { assertNoModeTabs(it) }
    }

    /**
     * A capture session hides the strip but used to keep its tabs, so the swipe handlers -- which
     * read the tab model, not the strip -- could still switch modes there.
     */
    @Test
    fun captureSessions_buildNoModeTabs() {
        launchCaptureSession(CaptureActivity::class.java, MediaStore.ACTION_IMAGE_CAPTURE)
            .use { assertNoModeTabs(it) }
        launchCaptureSession(VideoCaptureActivity::class.java, MediaStore.ACTION_VIDEO_CAPTURE)
            .use { assertNoModeTabs(it) }
    }

    /**
     * An app that asked for a single photo got the camera switched to another mode behind its back
     * when the user flung the preview, because only the strip was hidden, not the tabs behind it.
     */
    @Test
    fun flingInACaptureSession_leavesTheModeAlone() {
        // Prove the synthetic fling reaches onFling() at all: without this leg the assertion below
        // would hold just as well for a fling that never registered.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)

            scenario.onActivity { activity ->
                val tabs = activity.tabLayout
                val next = tabs.getTabAt(tabs.selectedTabPosition + 1)
                assertNotNull("no mode to the left of ${activity.camConfig.currentMode}", next)

                flingLeft(activity)
                assertEquals(next!!.tag as CameraMode, activity.camConfig.currentMode)
            }
        }

        launchCaptureSession(CaptureActivity::class.java, MediaStore.ACTION_IMAGE_CAPTURE)
            .use { scenario ->
                waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

                // Flinging before the tabs were built would pass whether or not any get built
                Thread.sleep(TAB_BUILD_DWELL_MS)

                scenario.onActivity { activity ->
                    flingLeft(activity)
                    assertEquals(CameraMode.CAMERA, activity.camConfig.currentMode)
                }
            }
    }

    private fun <A : MainActivity> assertNoModeTabs(scenario: ActivityScenario<A>) {
        waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

        // Asserting straight after the bind would also hold while a build was merely still
        // pending, so outlast the extension probe round that used to precede one.
        Thread.sleep(TAB_BUILD_DWELL_MS)

        scenario.onActivity {
            assertEquals("${it.javaClass.simpleName} built mode tabs", 0, it.tabLayout.tabCount)
        }
    }

    private fun <A : MainActivity> launchCaptureSession(
        activityClass: Class<A>,
        action: String,
    ): ActivityScenario<A> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch(Intent(context, activityClass).setAction(action))
    }

    private companion object {
        private const val WINDOW_MS = 500L
        private const val TAB_BUILD_DWELL_MS = 2000L
    }
}
