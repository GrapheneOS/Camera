package app.grapheneos.camera.ui.mediaviewer.playback

import app.grapheneos.camera.data.media.model.VideoPlaybackState
import app.grapheneos.camera.data.media.player.VideoPlayback
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlaybackControllerTest {

    private val playbackState = MutableStateFlow(VideoPlaybackState())
    private val shownPosition = MutableStateFlow(Duration.ZERO)
    private var playerPosition = 10.seconds
    private val playback = mockk<VideoPlayback>(relaxed = true) {
        every { state } returns playbackState
        every { shownPosition } returns this@VideoPlaybackControllerTest.shownPosition
        every { currentPosition } answers { playerPosition }
    }
    private val followsPosition = MutableStateFlow(true)
    private val controller = VideoPlaybackController(playback)

    @Test
    fun togglePlayback_whilePlaying_pauses() {
        runTest {
            track()

            playbackState.value = VideoPlaybackState(isPlaying = true)

            controller.togglePlayback()

            verify { playback.pause() }
        }
    }

    @Test
    fun togglePlayback_whilePaused_plays() {
        runTest {
            track()

            controller.togglePlayback()

            verify { playback.play() }
        }
    }

    @Test
    fun aFailure_replacesThePlayback() {
        runTest {
            track()

            playbackState.value = VideoPlaybackState(hasFailed = true)

            assertEquals(VideoPlaybackUiModel.Failed, controller.state.value)
        }
    }

    @Test
    fun position_followsTheShownFrame() {
        runTest {
            track()

            shownPosition.value = 7.seconds

            assertEquals(7.seconds, controller.position.value)
        }
    }

    @Test
    fun position_isNotFollowedWhileNothingShowsIt() {
        runTest {
            track()

            followsPosition.value = false
            shownPosition.value = 7.seconds

            assertEquals(Duration.ZERO, controller.position.value)
        }
    }

    @Test
    fun followingAgain_catchesUpWithTheShownFrame() {
        runTest {
            track()

            followsPosition.value = false
            shownPosition.value = 7.seconds
            followsPosition.value = true

            assertEquals(7.seconds, controller.position.value)
        }
    }

    @Test
    fun seekTo_seeksAndShowsThePosition() {
        runTest {
            track()

            controller.seekTo(9.seconds)

            verify { playback.seekTo(9.seconds) }
            assertEquals(9.seconds, controller.position.value)
        }
    }

    @Test
    fun framesShownWhileSeeking_leaveTheThumbUnderTheFinger() {
        runTest {
            track()

            controller.seekTo(3.seconds)

            shownPosition.value = 7.seconds

            assertEquals(3.seconds, controller.position.value)
        }
    }

    @Test
    fun seeking_scrubsUntilFinished() {
        runTest {
            track()

            controller.seekTo(3.seconds)
            controller.seekTo(4.seconds)

            verify(exactly = 1) { playback.setScrubbing(true) }
            verify(exactly = 0) { playback.setScrubbing(false) }

            controller.finishSeek()

            verify(exactly = 1) { playback.setScrubbing(false) }
        }
    }

    @Test
    fun finishSeek_seeksExactlyToWhereTheFingerLifted() {
        runTest {
            track()

            controller.seekTo(3.seconds)
            controller.finishSeek()

            verifyOrder {
                playback.setScrubbing(false)
                playback.seekTo(3.seconds)
            }
        }
    }

    @Test
    fun seeking_whilePlaying_pausesUntilFinished() {
        runTest {
            track()

            playbackState.value = VideoPlaybackState(isPlaying = true)

            controller.seekTo(3.seconds)
            controller.seekTo(4.seconds)

            verify(exactly = 1) { playback.pause() }
            verify(exactly = 0) { playback.play() }

            controller.finishSeek()

            verify(exactly = 1) { playback.play() }
        }
    }

    @Test
    fun finishSeek_afterSeekingWhilePaused_staysPaused() {
        runTest {
            track()

            controller.seekTo(3.seconds)
            controller.finishSeek()

            verify(exactly = 0) { playback.play() }
        }
    }

    @Test
    fun finishSeek_whilePaused_keepsTheThumbWhereTheFingerLifted() {
        runTest {
            track()

            controller.seekTo(3.seconds)
            controller.finishSeek()
            shownPosition.value = 3.seconds + 20.milliseconds

            assertEquals(3.seconds, controller.position.value)
        }
    }

    @Test
    fun playing_afterSeekingWhilePaused_followsTheShownFrameAgain() {
        runTest {
            track()

            controller.seekTo(3.seconds)
            controller.finishSeek()
            controller.togglePlayback()
            shownPosition.value = 4.seconds

            assertEquals(4.seconds, controller.position.value)
        }
    }

    @Test
    fun fastForward_playsAPausedVideoFastUntilStoppedThenKeepsPlaying() {
        runTest {
            track()

            controller.startFastForward()

            verify { playback.setSpeed(2f) }
            verify { playback.play() }
            assertTrue(active().isFastForwarding)

            controller.stopFastForward()

            verify { playback.setSpeed(1f) }
            verify(exactly = 0) { playback.pause() }
            assertFalse(active().isFastForwarding)
        }
    }

    @Test
    fun skips_addUpInOneDirectionUntilTheyTimeOut() {
        runTest {
            track()

            controller.skip(SkipDirection.Forward)
            controller.skip(SkipDirection.Forward)

            assertEquals(
                VideoSkipUiModel(
                    direction = SkipDirection.Forward,
                    offset = 10.seconds,
                ),
                active().skip,
            )

            controller.skip(SkipDirection.Back)

            assertEquals(
                VideoSkipUiModel(
                    direction = SkipDirection.Back,
                    offset = 5.seconds,
                ),
                active().skip,
            )

            advanceTimeBy(500.milliseconds)
            runCurrent()

            assertNull(active().skip)
        }
    }

    @Test
    fun skips_nearTheEnd_reachItThenStopAddingUp() {
        runTest {
            track()

            playbackState.value = VideoPlaybackState(duration = 12.seconds)

            playerPosition = 5.seconds
            controller.skip(SkipDirection.Forward)
            playerPosition = 10.seconds
            controller.skip(SkipDirection.Forward)
            playerPosition = 12.seconds
            controller.skip(SkipDirection.Forward)

            verifyOrder {
                playback.seekTo(10.seconds)
                playback.seekTo(12.seconds)
            }
            verify(exactly = 2) { playback.seekTo(any()) }
            assertEquals(
                VideoSkipUiModel(
                    direction = SkipDirection.Forward,
                    offset = 7.seconds,
                ),
                active().skip,
            )
        }
    }

    @Test
    fun skip_atTheEnd_showsAStepWithoutSeeking() {
        runTest {
            track()

            playbackState.value = VideoPlaybackState(duration = 12.seconds)
            playerPosition = 12.seconds

            controller.skip(SkipDirection.Forward)

            verify(exactly = 0) { playback.seekTo(any()) }
            assertEquals(
                VideoSkipUiModel(
                    direction = SkipDirection.Forward,
                    offset = 5.seconds,
                ),
                active().skip,
            )
        }
    }

    @Test
    fun skip_showsItsTargetUntilThePlayerShowsAFrameThere() {
        runTest {
            track()

            shownPosition.value = 10.seconds
            controller.skip(SkipDirection.Forward)
            shownPosition.value = 10.seconds + 40.milliseconds

            assertEquals(15.seconds, controller.position.value)

            shownPosition.value = 15.seconds + 20.milliseconds

            assertEquals(15.seconds + 20.milliseconds, controller.position.value)
        }
    }

    private fun TestScope.track() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.track(followsPosition)
        }
    }

    private fun active(): VideoPlaybackUiModel.Active {
        return requireNotNull(controller.state.value as? VideoPlaybackUiModel.Active)
    }
}
