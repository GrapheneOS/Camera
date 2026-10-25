package app.grapheneos.camera.ui.videoplayer.screen

import android.net.Uri
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.media.model.VideoPlaybackState
import app.grapheneos.camera.data.media.player.VideoPlayback
import app.grapheneos.camera.data.media.player.VideoPlaybackFactory
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerAction as Action
import app.grapheneos.camera.ui.videoplayer.screen.model.VideoPlayerScreenEffect as Effect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VideoPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playbackState = MutableStateFlow(VideoPlaybackState())
    private val shownPosition = MutableStateFlow(Duration.ZERO)
    private val playback = mockk<VideoPlayback>(relaxed = true) {
        every { state } returns playbackState
        every { shownPosition } returns this@VideoPlayerViewModelTest.shownPosition
    }
    private val videoPlaybackFactory = mockk<VideoPlaybackFactory> {
        every { create() } returns playback
    }

    @Test
    fun opening_loadsTheVideo() {
        runTest {
            viewModel()

            verify { playback.load(VIDEO_URI) }
        }
    }

    @Test
    fun aPlaybackFailure_closesThePlayerOnce() {
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<Effect>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.effects.toList(effects)
            }

            playbackState.value = VideoPlaybackState(hasFailed = true)
            playbackState.value = VideoPlaybackState(hasFailed = true, duration = 12.seconds)

            assertEquals(listOf(Effect.PlaybackFailed), effects)
        }
    }

    @Test
    fun playPauseClicked_whilePaused_plays() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.PlayPauseClicked)

            verify { playback.play() }
        }
    }

    @Test
    fun muteClicked_mutesAndUnmutesThePlayback() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.MuteClicked)
            assertTrue(viewModel.uiState.value.isMuted)
            verify { playback.setMuted(true) }

            viewModel.onAction(Action.MuteClicked)
            assertFalse(viewModel.uiState.value.isMuted)
            verify { playback.setMuted(false) }
        }
    }

    @Test
    fun aMutedVideo_staysMutedWhenTheProcessIsRecreated() {
        runTest {
            val arguments = playerArguments()
            viewModel(arguments).onAction(Action.MuteClicked)

            val recreated = viewModel(arguments)

            assertTrue(recreated.uiState.value.isMuted)
            verify(exactly = 2) { playback.setMuted(true) }
        }
    }

    @Test
    fun position_followsTheShownFrame() {
        runTest {
            val viewModel = viewModel()

            shownPosition.value = 7.seconds

            assertEquals(7.seconds, viewModel.uiState.value.position)
        }
    }

    @Test
    fun position_isNotFollowedWhileTheControlsAreHidden() {
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(Action.ScreenTapped)

            shownPosition.value = 7.seconds

            assertEquals(Duration.ZERO, viewModel.uiState.value.position)
        }
    }

    @Test
    fun seekDragged_seeksAndShowsThePosition() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.SeekDragged(9.seconds))

            verify { playback.seekTo(9.seconds) }
            assertEquals(9.seconds, viewModel.uiState.value.position)
        }
    }

    @Test
    fun screenTapped_togglesTheControls() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.ScreenTapped)
            assertFalse(viewModel.uiState.value.areControlsVisible)

            viewModel.onAction(Action.ScreenTapped)
            assertTrue(viewModel.uiState.value.areControlsVisible)
        }
    }

    @Test
    fun shownControls_stayWhilePlaying() {
        runTest {
            val viewModel = viewModel()
            playbackState.value = VideoPlaybackState(isPlaying = true)

            advanceTimeBy(10.seconds)

            assertTrue(viewModel.uiState.value.areControlsVisible)
        }
    }

    @Test
    fun screenStopped_pausesThePlayback() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.ScreenStopped)

            verify { playback.pause() }
        }
    }

    private fun viewModel(arguments: SavedStateHandle = playerArguments()): VideoPlayerViewModel {
        return VideoPlayerViewModel(
            arguments = arguments,
            videoPlaybackFactory = videoPlaybackFactory,
        )
    }

    private fun playerArguments(): SavedStateHandle {
        val arguments = videoPlayerArguments(VIDEO_URI)

        return SavedStateHandle(
            arguments.keySet().associateWith { key ->
                BundleCompat.getParcelable(arguments, key, Uri::class.java)
            },
        )
    }

    private companion object {
        val VIDEO_URI: Uri = Uri.parse("content://media/external/video/media/1")
    }
}
