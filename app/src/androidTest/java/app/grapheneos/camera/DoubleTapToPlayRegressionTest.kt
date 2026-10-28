package app.grapheneos.camera

import android.Manifest
import android.app.Application
import android.app.Instrumentation
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.InAppGallery
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A second tap on a video before its player showed started a second player on top of the first,
 * which then took two Backs to leave.
 */
@RunWith(AndroidJUnit4::class)
class DoubleTapToPlayRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val stall = StalledMediaScan()
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()
    private var video: Uri? = null

    @After
    fun cleanUp() {
        monitors.forEach { instrumentation.removeMonitor(it) }
        stall.gate.countDown()
        app.unregisterActivityLifecycleCallbacks(stall)
        video?.let { instrumentation.targetContext.contentResolver.delete(it, null, null) }
    }

    @Test
    fun doubleTap_onAGalleryVideoPage_startsOnePlayer() {
        val item = CapturedItem(ITEM_TYPE_VIDEO, "20261015_000000", recordAVideo())
        val starts = monitorPlayerStarts(block = true)

        openGallery(item).use { scenario ->
            scenario.onActivity {
                val page = it.findViewById<View>(R.id.slide_preview)
                page.performClick()
                page.performClick()
            }

            assertEquals(1, starts.hits)
        }
    }

    @Test
    fun doubleTap_onACaptureSessionsPlayButton_startsOnePlayer() {
        val starts = monitorPlayerStarts(block = true)

        launchCaptureSession(VideoCaptureActivity::class.java, MediaStore.ACTION_VIDEO_CAPTURE)
            .use { scenario ->
                recordAVideo(scenario)

                scenario.onActivity {
                    val playButton = it.findViewById<View>(R.id.play_preview)
                    playButton.performClick()
                    playButton.performClick()
                }

                assertEquals(1, starts.hits)
            }
    }

    @Test
    fun tap_afterThePlayerIsClosed_startsItAgain() {
        val item = CapturedItem(ITEM_TYPE_VIDEO, "20261015_000000", recordAVideo())
        val opened = monitorPlayerStarts(block = false)

        openGallery(item).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.slide_preview).performClick() }
            val player = opened.waitForActivityWithTimeout(10_000)
            assertNotNull("The player did not open", player)
            instrumentation.runOnMainSync { player.finish() }
            waitUntil(scenario, "the gallery is back") {
                it.lifecycle.currentState == Lifecycle.State.RESUMED
            }

            // Stops this one from opening a player that would then need closing
            val starts = monitorPlayerStarts(block = true)
            scenario.onActivity { it.findViewById<View>(R.id.slide_preview).performClick() }

            assertEquals(1, starts.hits)
        }
    }

    /** A blocking monitor stops the start, so nothing is left to close. */
    private fun monitorPlayerStarts(block: Boolean): Instrumentation.ActivityMonitor {
        // Only the first matching monitor counts a start
        monitors.forEach { instrumentation.removeMonitor(it) }
        return instrumentation.addMonitor(VideoPlayer::class.java.name, null, block)
            .also { monitors.add(it) }
    }

    private fun recordAVideo(): Uri =
        launchCaptureSession(VideoCaptureActivity::class.java, MediaStore.ACTION_VIDEO_CAPTURE)
            .use { recordAVideo(it) }

    private fun recordAVideo(scenario: ActivityScenario<VideoCaptureActivity>): Uri {
        awaitShutterReady(scenario)
        scenario.onActivity { it.camConfig.mPlayer = ImmediateTunePlayer(it) }
        val capturedBefore = lastCapturedUri(scenario)
        scenario.onActivity { it.captureButton.performClick() }
        try {
            recordASecond(scenario)
        } finally {
            scenario.onActivity { it.captureButton.performClick() }
        }
        awaitConfirmScreen(scenario)
        awaitOutputSettled(scenario)

        val uri = lastCapturedUri(scenario)
        check(uri != null && uri != capturedBefore) { "The video was not saved" }
        video = uri
        return uri
    }

    private fun openGallery(item: CapturedItem): ActivityScenario<InAppGallery> {
        // Keeps the device's own media out of the gallery, which then shows only this item
        app.registerActivityLifecycleCallbacks(stall)
        val scenario = ActivityScenario.launch(InAppGallery::class.java)
        scenario.onActivity { activity ->
            GallerySliderAdapter(activity, arrayListOf(item)).let {
                activity.gallerySliderAdapter = it
                activity.gallerySlider.adapter = it
            }
        }
        waitUntil(scenario, "the video can be played") {
            it.findViewById<View>(R.id.slide_preview)?.hasOnClickListeners() == true
        }
        return scenario
    }
}
