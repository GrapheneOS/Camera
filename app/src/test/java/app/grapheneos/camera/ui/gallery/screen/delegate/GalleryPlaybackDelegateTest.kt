package app.grapheneos.camera.ui.gallery.screen.delegate

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.VideoOutput
import app.grapheneos.camera.data.media.model.VideoOutputChange
import app.grapheneos.camera.data.media.model.VideoPlaybackState
import app.grapheneos.camera.data.media.player.VideoPlayback
import app.grapheneos.camera.data.media.player.VideoPlaybackFactory
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GalleryPlaybackDelegateTest {

    private val playbacks = mutableListOf<VideoPlayback>()
    private val videoPlaybackFactory = mockk<VideoPlaybackFactory> {
        every { create() } answers {
            val playback = mockk<VideoPlayback>(relaxed = true) {
                every { state } returns MutableStateFlow(VideoPlaybackState())
                every { shownPosition } returns MutableStateFlow(Duration.ZERO)
                every { currentPosition } returns 10.seconds
            }
            playbacks += playback
            playback
        }
    }
    private val stateHolder = GalleryStateHolder(
        GalleryState(items = listOf(PHOTO, VIDEO, OTHER_VIDEO)),
    )

    @Test
    fun aSettledVideo_playsUntilAnotherPageSettles() {
        runTest {
            createDelegate()

            settle(page = 1)

            verify { playbacks.single().load(VIDEO.uri) }
            assertEquals(VIDEO, stateHolder.state.value.video?.item)

            settle(page = 0)

            verify { playbacks.single().unload() }
            assertNull(stateHolder.state.value.video)
        }
    }

    @Test
    fun theNextVideo_reusesThePlayer() {
        runTest {
            createDelegate()

            settle(page = 1)
            settle(page = 2)

            verify(exactly = 1) { videoPlaybackFactory.create() }
            verifyOrder {
                playbacks.single().load(VIDEO.uri)
                playbacks.single().load(OTHER_VIDEO.uri)
            }
        }
    }

    @Test
    fun aSettledPhoto_playsNothing() {
        runTest {
            createDelegate()

            settle(page = 0)

            verify(exactly = 0) { videoPlaybackFactory.create() }
        }
    }

    @Test
    fun toggleMute_keepsTheNextVideosMuted() {
        runTest {
            val delegate = createDelegate()

            settle(page = 1)
            delegate.toggleMute()
            settle(page = 2)

            verifyOrder {
                playbacks.single().load(OTHER_VIDEO.uri)
                playbacks.single().setMuted(true)
            }
            assertTrue(stateHolder.state.value.playbackOptions.isMuted)
        }
    }

    @Test
    fun chosenSpeedAndLooping_carryOverToTheNextVideo() {
        runTest {
            val delegate = createDelegate()

            settle(page = 1)
            delegate.setSpeed(1.5f)
            delegate.toggleLooping()
            settle(page = 2)

            verifyOrder {
                playbacks.single().setLooping(true)
                playbacks.single().load(OTHER_VIDEO.uri)
                playbacks.single().setLooping(false)
            }
            verifyOrder {
                playbacks.single().load(OTHER_VIDEO.uri)
                playbacks.single().setSpeed(1.5f)
            }
        }
    }

    @Test
    fun openDetails_silenceTheVideoWithoutMutingIt() {
        runTest {
            createDelegate()

            settle(page = 1)
            stateHolder.update { it.copy(showsDetails = true) }

            verify { playbacks.single().setMuted(true) }
            assertFalse(stateHolder.state.value.playbackOptions.isMuted)

            stateHolder.update { it.copy(showsDetails = false) }

            verify { playbacks.single().setMuted(false) }
        }
    }

    @Test
    fun position_followsTheSettledVideoUntilItStops() {
        runTest {
            val delegate = createDelegate()

            settle(page = 1)
            (playbacks.single().shownPosition as MutableStateFlow).value = 7.seconds

            assertEquals(7.seconds, delegate.position.value)

            settle(page = 0)

            assertEquals(Duration.ZERO, delegate.position.value)
        }
    }

    @Test
    fun aVideoThatStopped_stillDetachesItsOutput() {
        runTest {
            val delegate = createDelegate()
            val detached = VideoOutputChange.Detached(
                VideoOutput(
                    id = 1,
                    surface = mockk(),
                ),
            )

            settle(page = 1)
            settle(page = 0)
            delegate.changeOutput(detached)

            verify { playbacks.single().changeOutput(detached) }
        }
    }

    private fun settle(page: Int) {
        stateHolder.update { it.copy(settledIndex = page) }
    }

    private fun TestScope.createDelegate(): GalleryPlaybackDelegate {
        val delegate = GalleryPlaybackDelegateImpl(videoPlaybackFactory)

        delegate.bind(stateHolder)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.trackPlayback()
        }

        return delegate
    }

    private companion object {
        val PHOTO = item(
            type = CapturedItemType.IMAGE,
            dateString = "20231016_120000_000",
        )
        val VIDEO = item(
            type = CapturedItemType.VIDEO,
            dateString = "20231015_120000_000",
        )
        val OTHER_VIDEO = item(
            type = CapturedItemType.VIDEO,
            dateString = "20231014_120000_000",
        )

        fun item(
            type: CapturedItemType,
            dateString: String,
        ): CapturedItem {
            return CapturedItem(
                type = type,
                dateString = dateString,
                uri = Uri.parse("content://media/external/file/$dateString"),
            )
        }
    }
}
