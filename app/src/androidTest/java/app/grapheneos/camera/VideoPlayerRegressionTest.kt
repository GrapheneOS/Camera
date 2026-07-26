package app.grapheneos.camera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.ui.activities.VideoPlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlayerRegressionTest {

    /** Launching an activity into a dozing, locked device gives it no visible window. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * Regression test for the production crash: an IllegalStateException out of
     * VideoView.setVideoURI took down the process instead of closing the player.
     */
    @Test
    fun videoLoad_whenMediaServiceDies_closesPlayerInsteadOfCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse("content://media/external/video/media/0")
        val intent = Intent(context, VideoPlayer::class.java)
            .putExtra(VideoPlayer.VIDEO_URI, uri)

        ActivityScenario.launch<VideoPlayer>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)

                activity.playVideo(DeadMediaServiceVideoView(activity), uri)

                assertTrue(activity.isFinishing)
            }
        }
    }

    /**
     * Behaves like VideoView when the media service dies during playback setup: openVideo()
     * catches IOException and IllegalArgumentException but lets prepareAsync()'s
     * IllegalStateException escape to the caller.
     */
    private class DeadMediaServiceVideoView(context: Context) : VideoView(context) {
        override fun setVideoURI(uri: Uri?) {
            throw IllegalStateException("prepareAsync called in state 0")
        }
    }
}
