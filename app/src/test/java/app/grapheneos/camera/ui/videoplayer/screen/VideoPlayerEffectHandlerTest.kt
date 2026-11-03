package app.grapheneos.camera.ui.videoplayer.screen

import androidx.activity.ComponentActivity
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerScreenEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class VideoPlayerEffectHandlerTest {

    @Test
    fun playbackFailure_showsTheErrorAndAsksTheHostToGoBack() {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = host.get()
        var closed = false
        val handler = VideoPlayerEffectHandlerImpl(
            activity = activity,
            onNavigateBack = { closed = true },
        )

        try {
            handler.handle(VideoPlayerScreenEffect.PlaybackFailed)

            val expectedMessage = activity.getString(R.string.unable_to_play_video)
            assertEquals(1, ShadowToast.shownToastCount())
            assertTrue(ShadowToast.showedToast(expectedMessage))
            assertTrue(closed)
            assertFalse(activity.isFinishing)
        } finally {
            host.pause().stop().destroy()
        }
    }
}
