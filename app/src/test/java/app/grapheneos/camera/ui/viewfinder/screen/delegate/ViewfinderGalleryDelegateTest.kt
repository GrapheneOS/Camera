package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSession
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderGalleryDelegateTest {

    private val capturedItemSession = mockk<CapturedItemSession>(relaxed = true)

    private val capturedItemRepository = mockk<CapturedItemRepository>()

    private val reverted = CompletableDeferred<Unit>()

    private var isRevertFinished = false

    private val revertToMediaStoreLocation = mockk<RevertToMediaStoreLocation> {
        coEvery { this@mockk.invoke() } coAnswers {
            reverted.await()
            isRevertFinished = true
        }
    }

    private val loadedItem: CapturingSlot<CapturedItem> = slot()

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun refreshThumbnail_loadsTheLastCapturedItemOnceTheSessionIsPrepared() {
        runTest {
            val prepared = CompletableDeferred<Unit>()
            coEvery { capturedItemSession.prepare() } coAnswers { prepared.await() }
            every { capturedItemSession.lastCapturedItem } returns ITEM
            val delegate = createDelegate()

            delegate.refreshThumbnail()
            assertNull(thumbnail())

            prepared.complete(Unit)

            assertSame(THUMBNAIL, thumbnail())
            assertSame(ITEM, loadedItem.captured)
            coVerifyOrder {
                capturedItemSession.prepare()
                capturedItemRepository.loadThumbnail(
                    item = ITEM,
                    targetWidth = THUMBNAIL_SIZE.width,
                    targetHeight = THUMBNAIL_SIZE.height,
                )
            }
        }
    }

    @Test
    fun bind_followsTheStoredLastCapturedItem() {
        runTest {
            createDelegate()

            coVerify(exactly = 1) { capturedItemSession.trackLastCapturedItem() }
        }
    }

    @Test
    fun refreshThumbnail_withNothingCaptured_clearsIt() {
        runTest {
            every { capturedItemSession.lastCapturedItem } returns null
            val delegate = createDelegate()
            delegate.showThumbnail(THUMBNAIL)

            delegate.refreshThumbnail()

            assertNull(thumbnail())
        }
    }

    @Test
    fun showThumbnail_winsOverALoadStillUnderway() {
        runTest {
            val loaded = CompletableDeferred<Bitmap?>()
            every { capturedItemSession.lastCapturedItem } returns ITEM
            val delegate = createDelegate(loadedThumbnail = { loaded.await() })
            val captured = createBitmap(2, 2)

            delegate.refreshThumbnail()
            delegate.showThumbnail(captured)
            loaded.complete(THUMBNAIL)

            assertSame(captured, thumbnail())
        }
    }

    @Test
    fun openGallery_handsOverTheLastItemOnlyOnceItsThumbnailLoaded() {
        runTest {
            every { capturedItemSession.lastCapturedItem } returns ITEM
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate(
                entryPoint = cameraEntryPoint(requiresVideoModeOnly = true),
            )

            delegate.openGallery()
            delegate.showThumbnail(THUMBNAIL)
            delegate.openGallery()

            assertEquals(
                listOf(
                    Effect.Gallery.Open(lastCapturedItem = null, videoOnly = true),
                    Effect.Gallery.Open(lastCapturedItem = ITEM, videoOnly = true),
                ),
                effects,
            )
        }
    }

    @Test
    fun openGallery_inASecureSession_showsOnlyWhatThisSessionCaptured() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate(entryPoint = cameraEntryPoint(isSecureSession = true))

            delegate.openGallery()
            delegate.recordCapturedItem(ITEM)
            delegate.openGallery()

            verify(exactly = 1) { capturedItemSession.recordCapturedItem(ITEM) }
            assertEquals(
                listOf(
                    Effect.ShowMessage(R.string.no_image),
                    Effect.Gallery.OpenSecure(
                        capturedItems = listOf(ITEM),
                        lastCapturedItem = null,
                    ),
                ),
                effects,
            )
        }
    }

    @Test
    fun recordCapturedItem_outsideASecureSession_keepsNoList() {
        runTest {
            val delegate = createDelegate()

            delegate.recordCapturedItem(ITEM)

            verify(exactly = 1) { capturedItemSession.recordCapturedItem(ITEM) }
            assertEquals(
                emptyList<CapturedItem>(),
                stateHolder.state.value.gallery.secureCapturedItems,
            )
        }
    }

    @Test
    fun shareLastCapturedItem_sharesItOrSaysWhyNot() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate()

            every { capturedItemSession.lastCapturedItem } returns null
            delegate.shareLastCapturedItem()
            every { capturedItemSession.lastCapturedItem } returns ITEM
            delegate.shareLastCapturedItem()

            assertEquals(
                listOf(
                    Effect.ShowMessage(
                        R.string.please_wait_for_image_to_get_captured_before_sharing,
                    ),
                    Effect.Gallery.Share(ITEM),
                ),
                effects,
            )
        }
    }

    @Test
    fun shareLastCapturedItem_inASecureSession_isNotAllowed() {
        runTest {
            every { capturedItemSession.lastCapturedItem } returns ITEM
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate(entryPoint = cameraEntryPoint(isSecureSession = true))

            delegate.shareLastCapturedItem()

            assertEquals(listOf(Effect.ShowMessage(R.string.sharing_not_allowed)), effects)
        }
    }

    @Test
    fun storageLocationNotFound_showsTheDialogOnlyOnceTheLocationIsReverted() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.onStorageLocationNotFound()
            assertTrue(effects.isEmpty())

            reverted.complete(Unit)

            assertEquals(listOf(Effect.ShowStorageLocationNotFound), effects)
            coVerify(exactly = 1) { revertToMediaStoreLocation() }
        }
    }

    @Test
    fun storageLocationNotFound_finishesTheRevertEvenWhenTheScreenGoesAway() {
        runTest {
            val screenScope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val delegate = createDelegate(scope = screenScope)

            delegate.onStorageLocationNotFound()
            screenScope.cancel()
            reverted.complete(Unit)

            assertTrue(isRevertFinished)
        }
    }

    private fun thumbnail(): Bitmap? {
        return stateHolder.state.value.gallery.thumbnail
    }

    private fun TestScope.createDelegate(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
        scope: CoroutineScope = backgroundScope,
        loadedThumbnail: suspend () -> Bitmap? = { THUMBNAIL },
    ): ViewfinderGalleryDelegate {
        coEvery {
            capturedItemRepository.loadThumbnail(
                item = capture(loadedItem),
                targetWidth = any(),
                targetHeight = any(),
            )
        } coAnswers { loadedThumbnail() }

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val delegate = ViewfinderGalleryDelegateImpl(
            capturedItemSession = capturedItemSession,
            capturedItemRepository = capturedItemRepository,
            entryPoint = entryPoint,
            revertToMediaStoreLocation = revertToMediaStoreLocation,
            applicationScope = backgroundScope,
            mainDispatcher = dispatcher,
        )

        delegate.bind(
            scope = scope,
            stateHolder = stateHolder,
        )
        delegate.onScreenCreated(
            ViewfinderHost(
                previewTarget = mockk(relaxed = true),
                previewFrames = mockk(relaxed = true),
                thumbnailSize = THUMBNAIL_SIZE,
            ),
        )

        return delegate
    }

    private companion object {
        val THUMBNAIL: Bitmap = createBitmap(1, 1)
        val THUMBNAIL_SIZE = ThumbnailSize(width = 64, height = 64)

        val ITEM = CapturedItem(
            type = CapturedItemType.IMAGE,
            dateString = "20260920_120000_000",
            uri = Uri.EMPTY,
        )
    }
}
