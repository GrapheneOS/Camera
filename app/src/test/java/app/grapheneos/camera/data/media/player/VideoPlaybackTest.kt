package app.grapheneos.camera.data.media.player

import android.view.Surface
import androidx.media3.exoplayer.ExoPlayer
import app.grapheneos.camera.data.media.model.VideoOutput
import app.grapheneos.camera.data.media.model.VideoOutputChange
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class VideoPlaybackTest {

    private val player = mockk<ExoPlayer>(relaxed = true)
    private val playback = VideoPlaybackImpl(player)

    @Test
    fun detachOutput_clearsTheAttachedSurface() {
        val output = output(id = 1)

        playback.changeOutput(VideoOutputChange.Attached(output))
        playback.changeOutput(VideoOutputChange.Detached(output))

        verify { player.clearVideoSurface(output.surface) }
    }

    @Test
    fun detachOutput_ofAReplacedOutput_keepsTheNewOne() {
        val previous = output(id = 1)
        val next = output(id = 2)

        playback.changeOutput(VideoOutputChange.Attached(previous))
        playback.changeOutput(VideoOutputChange.Attached(next))
        playback.changeOutput(VideoOutputChange.Detached(previous))

        verify(exactly = 0) { player.clearVideoSurface(any<Surface>()) }
    }

    private fun output(id: Int): VideoOutput {
        return VideoOutput(
            id = id,
            surface = mockk(),
        )
    }
}
