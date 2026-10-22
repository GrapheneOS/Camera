package app.grapheneos.camera.domain.gallery.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapperImpl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObserveGalleryItemsTest {

    private val repository = mockk<CapturedItemRepository>()

    @Test
    fun theItems_areListedAtOnceAndAgainOnEveryChange() {
        runTest {
            every { repository.capturedItemsChanges() } returns flowOf(Unit)
            coEvery { repository.capturedItems() } returnsMany listOf(
                listOf(NEWER, OLDER),
                listOf(OLDER),
            )

            val emissions = galleryItems(refreshes = emptyFlow()).toList()

            assertEquals(listOf(listOf(NEWER, OLDER), listOf(OLDER)), emissions)
        }
    }

    @Test
    fun aRefresh_dropsTheReadInProgress() {
        runTest {
            val staleRead = CompletableDeferred<List<CapturedItem>>()
            val refreshes = MutableSharedFlow<Unit>()
            val emissions = mutableListOf<List<CapturedItem>>()
            every { repository.capturedItemsChanges() } returns emptyFlow()
            coEvery { repository.capturedItems() } coAnswers { staleRead.await() } andThen
                listOf(OLDER)

            backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                galleryItems(refreshes).toList(emissions)
            }
            runCurrent()
            refreshes.emit(Unit)
            runCurrent()
            staleRead.complete(listOf(NEWER, OLDER))
            runCurrent()

            assertEquals(listOf(listOf(OLDER)), emissions)
        }
    }

    private fun galleryItems(refreshes: Flow<Unit>): Flow<List<CapturedItem>> {
        val observeGalleryItems = ObserveGalleryItemsImpl(
            capturedItemRepository = repository,
            galleryItemsMapper = GalleryItemsMapperImpl(),
        )

        return observeGalleryItems.invoke(
            videoOnly = false,
            sessionItems = null,
            refreshes = refreshes,
        )
    }

    private companion object {
        val NEWER = image("20231016_120000_000")
        val OLDER = image("20231015_120000_000")

        fun image(dateString: String): CapturedItem {
            return CapturedItem(
                type = CapturedItemType.IMAGE,
                dateString = dateString,
                uri = Uri.parse("content://media/external/images/media/$dateString"),
            )
        }
    }
}
