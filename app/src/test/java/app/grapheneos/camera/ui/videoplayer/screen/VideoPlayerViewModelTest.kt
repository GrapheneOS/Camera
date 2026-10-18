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
import io.mockk.verifyOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
        every { create(VIDEO_URI) } returns playback
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
    fun playPauseClicked_whilePlaying_pauses() {
        runTest {
            val viewModel = viewModel()
            playbackState.value = VideoPlaybackState(isPlaying = true)

            viewModel.onAction(Action.PlayPauseClicked)

            verify { playback.pause() }
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
    fun shownControls_catchUpWithTheShownFrame() {
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(Action.ScreenTapped)

            shownPosition.value = 7.seconds

            viewModel.onAction(Action.ScreenTapped)

            assertEquals(7.seconds, viewModel.uiState.value.position)
        }
    }

    @Test
    fun framesShownWhileSeeking_leaveTheThumbUnderTheFinger() {
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(Action.SeekDragged(3.seconds))

            shownPosition.value = 7.seconds

            assertEquals(3.seconds, viewModel.uiState.value.position)
        }
    }

    @Test
    fun seekReleased_seeksExactlyToWhereTheFingerLifted() {
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(Action.SeekDragged(3.seconds))

            viewModel.onAction(Action.SeekReleased)

            verifyOrder {
                playback.setScrubbing(false)
                playback.seekTo(3.seconds)
            }
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
    fun seekDragged_whilePlaying_pausesUntilReleased() {
        runTest {
            val viewModel = viewModel()
            playbackState.value = VideoPlaybackState(isPlaying = true)

            viewModel.onAction(Action.SeekDragged(3.seconds))
            viewModel.onAction(Action.SeekDragged(4.seconds))

            verify(exactly = 1) { playback.pause() }
            verify(exactly = 0) { playback.play() }

            viewModel.onAction(Action.SeekReleased)

            verify(exactly = 1) { playback.play() }
        }
    }

    @Test
    fun seekDragged_scrubsUntilReleased() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.SeekDragged(3.seconds))
            viewModel.onAction(Action.SeekDragged(4.seconds))

            verify(exactly = 1) { playback.setScrubbing(true) }
            verify(exactly = 0) { playback.setScrubbing(false) }

            viewModel.onAction(Action.SeekReleased)

            verify(exactly = 1) { playback.setScrubbing(false) }
        }
    }

    @Test
    fun seekReleased_afterSeekingWhilePaused_staysPaused() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.SeekDragged(3.seconds))
            viewModel.onAction(Action.SeekReleased)

            verify(exactly = 0) { playback.play() }
        }
    }

    @Test
    fun seekReleased_whilePaused_keepsTheThumbWhereTheFingerLifted() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.SeekDragged(3.seconds))
            viewModel.onAction(Action.SeekReleased)

            shownPosition.value = 3.seconds + 20.milliseconds

            assertEquals(3.seconds, viewModel.uiState.value.position)
        }
    }

    @Test
    fun playing_afterSeekingWhilePaused_followsTheShownFrameAgain() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.SeekDragged(3.seconds))
            viewModel.onAction(Action.SeekReleased)
            viewModel.onAction(Action.PlayPauseClicked)

            shownPosition.value = 4.seconds

            assertEquals(4.seconds, viewModel.uiState.value.position)
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

    private fun viewModel(): VideoPlayerViewModel {
        val arguments = videoPlayerArguments(VIDEO_URI)

        return VideoPlayerViewModel(
            arguments = SavedStateHandle(
                arguments.keySet().associateWith { key ->
                    BundleCompat.getParcelable(arguments, key, Uri::class.java)
                },
            ),
            videoPlaybackFactory = videoPlaybackFactory,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )
    }

    private companion object {
        val VIDEO_URI: Uri = Uri.parse("content://media/external/video/media/1")
    }
}
