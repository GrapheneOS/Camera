package app.grapheneos.camera.ui.gallery.screen.delegate

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.ui.gallery.screen.GalleryStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GalleryPreviewsDelegateTest {

    private val capturedItemRepository = mockk<CapturedItemRepository> {
        coEvery { loadThumbnail(any(), any(), any()) } returns THUMBNAIL
        coEvery { loadPhotoPreview(any()) } returns PREVIEW
        coEvery { loadFullPhoto(any()) } returns FULL_PHOTO
    }
    private val stateHolder = GalleryStateHolder(GalleryState(items = listOf(PHOTO, VIDEO)))

    @Test
    fun aVideo_getsItsThumbnailAndNoPreview() {
        runTest {
            trackPreviews()

            val state = stateHolder.state.value

            assertFalse(VIDEO in state.previews)
            assertEquals(THUMBNAIL, bitmapOf(state.thumbnails[VIDEO]))
            coVerify(exactly = 0) { capturedItemRepository.loadPhotoPreview(VIDEO) }
        }
    }

    @Test
    fun aZoomedPhoto_isLoadedAtFullResolutionUntilThePageChanges() {
        runTest {
            trackPreviews()

            stateHolder.update { it.copy(isZoomed = true) }

            assertEquals(PHOTO, stateHolder.state.value.fullPhoto?.item)
            assertEquals(FULL_PHOTO, bitmapOf(stateHolder.state.value.fullPhoto?.image))

            stateHolder.update { it.copy(isZoomed = false) }

            assertEquals(PHOTO, stateHolder.state.value.fullPhoto?.item)

            stateHolder.update { it.copy(currentIndex = 1) }

            assertNull(stateHolder.state.value.fullPhoto)
        }
    }

    @Test
    fun zoomingAgain_keepsTheLoadedFullPhoto() {
        runTest {
            trackPreviews()

            stateHolder.update { it.copy(isZoomed = true) }
            stateHolder.update { it.copy(isZoomed = false) }
            stateHolder.update { it.copy(isZoomed = true) }

            coVerify(exactly = 1) { capturedItemRepository.loadFullPhoto(PHOTO) }
        }
    }

    @Test
    fun aZoomedVideo_loadsNoFullPhoto() {
        runTest {
            trackPreviews()

            stateHolder.update { it.copy(currentIndex = 1, isZoomed = true) }

            coVerify(exactly = 0) { capturedItemRepository.loadFullPhoto(any()) }
        }
    }

    private fun bitmapOf(preview: GalleryPreview?): Bitmap? {
        return (preview as? GalleryPreview.Loaded)?.image?.asAndroidBitmap()
    }

    private fun TestScope.trackPreviews() {
        val delegate = GalleryPreviewsDelegateImpl(capturedItemRepository)

        delegate.bind(stateHolder)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.trackPreviews()
        }
    }

    private companion object {
        val THUMBNAIL = mockk<Bitmap>(relaxed = true)
        val PREVIEW = mockk<Bitmap>(relaxed = true)
        val FULL_PHOTO = mockk<Bitmap>(relaxed = true)

        val PHOTO = item(
            type = CapturedItemType.IMAGE,
            dateString = "20231016_120000_000",
        )
        val VIDEO = item(
            type = CapturedItemType.VIDEO,
            dateString = "20231015_120000_000",
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
