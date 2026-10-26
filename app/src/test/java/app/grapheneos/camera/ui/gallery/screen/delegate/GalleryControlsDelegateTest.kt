package app.grapheneos.camera.ui.gallery.screen.delegate

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryVideoState
import app.grapheneos.camera.ui.mediaviewer.playback.VideoPlaybackUiModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GalleryControlsDelegateTest {

    private val stateHolder = GalleryStateHolder(GalleryState(items = listOf(VIDEO)))

    @Test
    fun aPlayingVideo_hidesTheControlsAfterFiveSeconds() {
        runTest {
            startHidingIdleControls()

            showVideo(isPlaying = true)
            advanceTimeBy(4.seconds)

            assertTrue(stateHolder.state.value.areControlsVisible)
            advanceTimeBy(2.seconds)
            assertFalse(stateHolder.state.value.areControlsVisible)
        }
    }

    @Test
    fun aPausedVideo_keepsTheControls() {
        runTest {
            startHidingIdleControls()

            showVideo(isPlaying = false)
            advanceTimeBy(10.seconds)

            assertTrue(stateHolder.state.value.areControlsVisible)
        }
    }

    @Test
    fun restartIdleTimer_keepsTheControlsForAnotherFiveSeconds() {
        runTest {
            val delegate = startHidingIdleControls()

            showVideo(isPlaying = true)
            advanceTimeBy(4.seconds)
            delegate.restartIdleTimer()
            advanceTimeBy(4.seconds)

            assertTrue(stateHolder.state.value.areControlsVisible)
            advanceTimeBy(2.seconds)
            assertFalse(stateHolder.state.value.areControlsVisible)
        }
    }

    @Test
    fun openDetails_keepTheControls() {
        runTest {
            startHidingIdleControls()

            showVideo(isPlaying = true)
            stateHolder.update { it.copy(showsDetails = true) }
            advanceTimeBy(10.seconds)

            assertTrue(stateHolder.state.value.areControlsVisible)
        }
    }

    @Test
    fun anOpenDialog_keepsTheControls() {
        runTest {
            startHidingIdleControls()

            showVideo(isPlaying = true)
            stateHolder.update { it.copy(dialog = GalleryDialog.PlaybackSpeed) }
            advanceTimeBy(10.seconds)

            assertTrue(stateHolder.state.value.areControlsVisible)
        }
    }

    private fun showVideo(isPlaying: Boolean) {
        stateHolder.update {
            it.copy(
                video = GalleryVideoState(
                    item = VIDEO,
                    playback = VideoPlaybackUiModel.Active(isPlaying = isPlaying),
                ),
            )
        }
    }

    private fun TestScope.startHidingIdleControls(): GalleryControlsDelegate {
        val delegate = GalleryControlsDelegateImpl()

        delegate.bind(stateHolder)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.hideIdleControls()
        }

        return delegate
    }

    private companion object {
        val VIDEO = CapturedItem(
            type = CapturedItemType.VIDEO,
            dateString = "20231015_120000_000",
            uri = Uri.parse("content://media/external/file/20231015_120000_000"),
        )
    }
}
