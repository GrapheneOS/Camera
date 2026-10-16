package app.grapheneos.camera

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.ui.videoplayer.VideoPlayerActivity
import app.grapheneos.camera.ui.videoplayer.screen.videoPlayerArguments
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlayerRegressionTest {

    /** Launching an activity into a dozing, locked device gives it no visible window. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * Regression test for the production crash: a video the media stack could not load took down
     * the process instead of closing the player.
     */
    @Test
    fun videoLoad_whenPlaybackFails_closesPlayerInsteadOfCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse("content://media/external/video/media/0")
        val intent = Intent(context, VideoPlayerActivity::class.java)
            .putExtras(videoPlayerArguments(uri))

        ActivityScenario.launch<VideoPlayerActivity>(intent).use { scenario ->
            waitUntilDestroyed(scenario, "the player closed")
        }
    }
}
