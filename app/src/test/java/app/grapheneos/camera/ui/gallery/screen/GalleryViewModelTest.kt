package app.grapheneos.camera.ui.gallery.screen

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.media.mapper.CaptureTimeMapperImpl
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.GeoCoordinates
import app.grapheneos.camera.data.media.repository.CapturedItemDetailsRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapperImpl
import app.grapheneos.camera.domain.gallery.model.CapturedItemDeletion
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.DeleteTrashedItemImpl
import app.grapheneos.camera.domain.gallery.usecase.ObserveGalleryItemsImpl
import app.grapheneos.camera.domain.gallery.usecase.RestoreCapturedItemImpl
import app.grapheneos.camera.domain.gallery.usecase.TrashCapturedItemImpl
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDeletionDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDetailsDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPreviewsDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapperImpl
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDialog as Dialog
import app.grapheneos.camera.ui.gallery.screen.model.GalleryMenuUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var storedItems = listOf(NEWEST, OLDER)
    private val capturedItemsChanges = MutableSharedFlow<Unit>()
    private val capturedItemRepository = mockk<CapturedItemRepository> {
        coEvery { capturedItems() } coAnswers { storedItems }
        every { capturedItemsChanges() } returns capturedItemsChanges
        coEvery { loadThumbnail(any(), any(), any()) } returns THUMBNAIL
        coEvery { loadPreview(any()) } returns PREVIEW
    }
    private val capturedItemDetailsRepository = mockk<CapturedItemDetailsRepository> {
        coEvery { details(any()) } returns null
    }
    private val deleteCapturedItem = mockk<DeleteCapturedItem>()
    private val capturedItemTrashRepository = mockk<CapturedItemTrashRepository> {
        every { canTrash(any()) } returns false
        coEvery { trash(any()) } coAnswers {
            storedItems = storedItems - firstArg<CapturedItem>()
            Result.success(Unit)
        }
        coEvery { restore(any()) } coAnswers {
            storedItems = storedItems + firstArg<CapturedItem>()
            Result.success(Unit)
        }
        coEvery { delete(any()) } returns Result.success(Unit)
    }

    @Before
    fun deleteSucceedsByDefault() {
        coEvery { deleteCapturedItem(any()) } coAnswers {
            storedItems = storedItems - firstArg<CapturedItem>()
            CapturedItemDeletion.Deleted
        }
    }

    @Test
    fun capturedItems_areShownAsPages() {
        runTest {
            val viewModel = viewModel()

            assertEquals(keysOf(NEWEST, OLDER), pageKeys(viewModel))
        }
    }

    @Test
    fun anEmptyGallery_showsThatItIsEmpty() {
        runTest {
            storedItems = emptyList()

            val viewModel = viewModel()

            assertTrue(viewModel.uiState.value.pager.isEmpty)
        }
    }

    @Test
    fun aGalleryStillLoading_isNotEmpty() {
        runTest {
            stallTheScan()

            assertFalse(viewModel().uiState.value.pager.isEmpty)
        }
    }

    @Test
    fun itemsDeletedByAnotherApp_disappear() {
        runTest {
            val viewModel = viewModel()

            storedItems = listOf(NEWEST)
            capturedItemsChanges.emit(Unit)

            assertEquals(keysOf(NEWEST), pageKeys(viewModel))
        }
    }

    @Test
    fun aRereadWithTheSameItems_keepsThePages() {
        runTest {
            val viewModel = viewModel()
            val pages = viewModel.uiState.value.pager.pages

            capturedItemsChanges.emit(Unit)

            assertSame(pages, viewModel.uiState.value.pager.pages)
        }
    }

    @Test
    fun theShownItemDeletedByAnotherApp_leavesTheNextOneInItsPlace() {
        runTest {
            val newer = image("20231017_120000_000")
            storedItems = listOf(newer, NEWEST, OLDER)
            val viewModel = viewModel()

            viewModel.onAction(Action.PageSelected(page = 1))
            storedItems = listOf(newer, OLDER)
            capturedItemsChanges.emit(Unit)

            assertEquals(1, viewModel.uiState.value.pager.currentPage)
            assertEquals(keysOf(newer, OLDER), pageKeys(viewModel))
        }
    }

    @Test
    fun theLastCapturedItem_keepsItsPreviewOnceTheScanListsIt() {
        runTest {
            val listed = CapturedItem(
                type = NEWEST.type,
                dateString = NEWEST.dateString,
                uri = Uri.parse("content://media/external/file/${NEWEST.dateString}"),
            )
            storedItems = listOf(listed, OLDER)

            val pager = viewModel(lastCapturedItem = NEWEST).uiState.value.pager
            val preview = pager.previews[pager.pages.first().key] as? GalleryPreview.Loaded

            assertEquals(PREVIEW, preview?.image?.asAndroidBitmap())
        }
    }

    @Test
    fun theLastCapturedItem_isShownBeforeTheScanFinishes() {
        runTest {
            stallTheScan()

            val viewModel = viewModel(lastCapturedItem = OLDER)

            assertEquals(keysOf(OLDER), pageKeys(viewModel))
        }
    }

    @Test
    fun theScan_keepsTheItemOnScreenCurrent() {
        runTest {
            val viewModel = viewModel(lastCapturedItem = OLDER)

            assertEquals(1, viewModel.uiState.value.pager.currentPage)
        }
    }

    @Test
    fun mediaActions_beforeTheScanFinishes_doNothing() {
        runTest {
            stallTheScan()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            listOf(
                Action.EditClicked,
                Action.EditWithClicked,
                Action.DeleteClicked,
                Action.DeleteConfirmed,
                Action.ShareClicked,
                Action.UseAsClicked,
                Action.CaptureTimeClicked,
                Action.DetailsShownChanged(isShown = true),
            ).forEach(viewModel::onAction)

            assertTrue(effects.isEmpty())
            assertNull(viewModel.uiState.value.dialog)
            assertFalse(viewModel.uiState.value.showsDetails)
        }
    }

    @Test
    fun aSecureSession_hidesAndRefusesTheExternalActions() {
        runTest {
            val viewModel = viewModel(entryPoint = cameraEntryPoint(isSecureSession = true))
            val effects = effectsOf(viewModel)

            coEvery { capturedItemDetailsRepository.details(NEWEST) } returns detailsAt(LOCATION)
            viewModel.onAction(Action.DetailsShownChanged(isShown = true))
            viewModel.onAction(Action.ShareClicked)
            viewModel.onAction(Action.UseAsClicked)
            viewModel.onAction(Action.EditClicked)
            viewModel.onAction(Action.SystemGalleryClicked)
            viewModel.onAction(Action.CaptureTimeClicked)
            viewModel.onAction(Action.OpenMapClicked)

            assertFalse(viewModel.uiState.value.bars.showsExternalActions)
            assertFalse(viewModel.uiState.value.bars.showsSystemGallery)
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun captureTimeClicked_showsTheItemInTheSystemGallery() {
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.CaptureTimeClicked)

            assertEquals(listOf(Effect.ShowInSystemGallery(NEWEST)), effects)
        }
    }

    @Test
    fun useAsClicked_offersThePhotoToOtherApps() {
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.UseAsClicked)

            assertEquals(GalleryMenuUiModel.Photo, viewModel.uiState.value.bars.menu)
            assertEquals(listOf(Effect.UseAs(NEWEST)), effects)
        }
    }

    @Test
    fun aVideo_getsTheVideoMenu() {
        runTest {
            storedItems = listOf(VIDEO)

            assertTrue(viewModel().uiState.value.bars.menu is GalleryMenuUiModel.Video)
        }
    }

    @Test
    fun deleteConfirmed_removesTheItem() {
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DeleteClicked)
            assertEquals(Dialog.DeleteConfirmation, viewModel.uiState.value.dialog)

            viewModel.onAction(Action.DeleteConfirmed)

            coVerify { deleteCapturedItem(NEWEST) }
            assertEquals(keysOf(OLDER), pageKeys(viewModel))
            assertEquals(listOf(Effect.ItemDeleted), effects)
        }
    }

    @Test
    fun deletingTheLastItem_leavesAnEmptyGallery() {
        runTest {
            storedItems = listOf(NEWEST)
            val viewModel = viewModel()

            viewModel.onAction(Action.DeleteClicked)
            viewModel.onAction(Action.DeleteConfirmed)

            assertTrue(viewModel.uiState.value.pager.isEmpty)
        }
    }

    @Test
    fun aFailedDeletion_keepsTheItem() {
        runTest {
            coEvery { deleteCapturedItem(any()) } returns CapturedItemDeletion.Failed
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DeleteClicked)
            viewModel.onAction(Action.DeleteConfirmed)

            assertEquals(2, viewModel.uiState.value.pager.pages.size)
            assertEquals(listOf(Effect.DeletionFailed), effects)
        }
    }

    @Test
    fun deleteClicked_onATrashableItem_trashesItWithoutAsking() {
        runTest {
            every { capturedItemTrashRepository.canTrash(any()) } returns true
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DeleteClicked)

            coVerify { capturedItemTrashRepository.trash(NEWEST) }
            assertNull(viewModel.uiState.value.dialog)
            assertEquals(keysOf(OLDER), pageKeys(viewModel))
            assertEquals(NEWEST.uri.toString(), viewModel.uiState.value.trashedPageKey)
            assertEquals(emptyList<Effect>(), effects)
        }
    }

    @Test
    fun aDeletion_listsTheItemsAgain() {
        runTest {
            every { capturedItemTrashRepository.canTrash(any()) } returns true
            val viewModel = viewModel()

            viewModel.onAction(Action.DeleteClicked)

            coVerify(exactly = 2) { capturedItemRepository.capturedItems() }
        }
    }

    @Test
    fun undoClicked_putsTheItemBackOnItsPage() {
        runTest {
            every { capturedItemTrashRepository.canTrash(any()) } returns true
            val viewModel = viewModel()

            viewModel.onAction(Action.PageSelected(page = 1))
            viewModel.onAction(Action.DeleteClicked)
            viewModel.onAction(Action.UndoClicked)

            coVerify { capturedItemTrashRepository.restore(OLDER) }
            assertEquals(keysOf(NEWEST, OLDER), pageKeys(viewModel))
            assertEquals(1, viewModel.uiState.value.pager.currentPage)
            assertNull(viewModel.uiState.value.trashedPageKey)
        }
    }

    @Test
    fun undoDismissed_deletesTheItemForGood() {
        runTest {
            every { capturedItemTrashRepository.canTrash(any()) } returns true
            val viewModel = viewModel()

            viewModel.onAction(Action.DeleteClicked)
            viewModel.onAction(Action.UndoDismissed)
            viewModel.onAction(Action.UndoClicked)

            coVerify { capturedItemTrashRepository.delete(NEWEST) }
            coVerify(exactly = 0) { capturedItemTrashRepository.restore(any()) }
            assertEquals(keysOf(OLDER), pageKeys(viewModel))
            assertNull(viewModel.uiState.value.trashedPageKey)
        }
    }

    @Test
    fun anotherDelete_deletesThePreviousItemForGood() {
        runTest {
            every { capturedItemTrashRepository.canTrash(any()) } returns true
            val viewModel = viewModel()

            viewModel.onAction(Action.DeleteClicked)
            viewModel.onAction(Action.DeleteClicked)

            coVerify { capturedItemTrashRepository.delete(NEWEST) }
            coVerify { capturedItemTrashRepository.trash(OLDER) }
            assertTrue(viewModel.uiState.value.pager.isEmpty)
        }
    }

    @Test
    fun aFailedTrash_keepsTheItem() {
        runTest {
            every { capturedItemTrashRepository.canTrash(any()) } returns true
            coEvery { capturedItemTrashRepository.trash(any()) } returns STORAGE_FAILURE
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DeleteClicked)

            assertEquals(keysOf(NEWEST, OLDER), pageKeys(viewModel))
            assertEquals(listOf(Effect.DeletionFailed), effects)
        }
    }

    @Test
    fun aFailedRestore_leavesTheItemDeleted() {
        runTest {
            every { capturedItemTrashRepository.canTrash(any()) } returns true
            coEvery { capturedItemTrashRepository.restore(any()) } returns STORAGE_FAILURE
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DeleteClicked)
            viewModel.onAction(Action.UndoClicked)

            assertEquals(keysOf(OLDER), pageKeys(viewModel))
            assertEquals(listOf(Effect.RestoreFailed), effects)
        }
    }

    @Test
    fun detailsShown_loadTheCurrentAndTheNeighbouringPages() {
        runTest {
            val newest = mockk<CapturedItemDetails>()
            val older = mockk<CapturedItemDetails>()
            coEvery { capturedItemDetailsRepository.details(NEWEST) } returns newest
            coEvery { capturedItemDetailsRepository.details(OLDER) } returns older
            val viewModel = viewModel()

            viewModel.onAction(Action.DetailsShownChanged(isShown = true))

            assertTrue(viewModel.uiState.value.showsDetails)
            assertEquals(GalleryDetails.Loaded(newest), detailsOf(viewModel, page = 0))
            assertEquals(GalleryDetails.Loaded(older), detailsOf(viewModel, page = 1))
        }
    }

    @Test
    fun detailsShown_whenTheyCannotBeRead_showThemAsUnavailable() {
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DetailsShownChanged(isShown = true))

            assertTrue(viewModel.uiState.value.showsDetails)
            assertEquals(GalleryDetails.Unavailable, detailsOf(viewModel, page = 0))
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun detailsHidden_keepsTheLoadedDetailsForNextTime() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.DetailsShownChanged(isShown = true))
            viewModel.onAction(Action.DetailsShownChanged(isShown = false))
            viewModel.onAction(Action.DetailsShownChanged(isShown = true))

            assertTrue(viewModel.uiState.value.showsDetails)
            coVerify(exactly = 1) { capturedItemDetailsRepository.details(NEWEST) }
        }
    }

    @Test
    fun detailsOfPagesFarAway_areDropped() {
        runTest {
            val items = (1..4).map { day -> image("2023101${day}_120000_000") }
            storedItems = items
            coEvery { capturedItemDetailsRepository.details(any()) } returns mockk()
            val viewModel = viewModel()

            viewModel.onAction(Action.DetailsShownChanged(isShown = true))
            viewModel.onAction(Action.PageSelected(page = 3))

            assertEquals(GalleryDetails.Loading, detailsOf(viewModel, page = 0))
            assertTrue(detailsOf(viewModel, page = 3) is GalleryDetails.Loaded)
        }
    }

    @Test
    fun anotherPage_whileDetailsAreHidden_loadsNoDetails() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.PageSelected(page = 1))

            assertEquals(GalleryDetails.Loading, detailsOf(viewModel, page = 1))
            coVerify(exactly = 0) { capturedItemDetailsRepository.details(any()) }
        }
    }

    @Test
    fun shownDetails_areShownAgainAfterTheProcessIsRecreated() {
        runTest {
            val details = mockk<CapturedItemDetails>()
            coEvery { capturedItemDetailsRepository.details(NEWEST) } returns details
            val arguments = savedStateOf(lastCapturedItem = null)
            viewModel(arguments = arguments).onAction(Action.DetailsShownChanged(isShown = true))

            val recreated = viewModel(arguments = arguments)

            assertTrue(recreated.uiState.value.showsDetails)
            assertEquals(GalleryDetails.Loaded(details), detailsOf(recreated, page = 0))
        }
    }

    @Test
    fun openMapClicked_opensTheMapAtTheShownLocation() {
        runTest {
            coEvery { capturedItemDetailsRepository.details(NEWEST) } returns detailsAt(LOCATION)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DetailsShownChanged(isShown = true))
            viewModel.onAction(Action.OpenMapClicked)

            assertEquals(listOf(Effect.OpenMap(LOCATION)), effects)
        }
    }

    @Test
    fun openMapClicked_withoutALocation_doesNothing() {
        runTest {
            coEvery { capturedItemDetailsRepository.details(NEWEST) } returns detailsAt(null)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DetailsShownChanged(isShown = true))
            viewModel.onAction(Action.OpenMapClicked)

            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun detailLongPressed_copiesItsText() {
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onAction(Action.DetailLongPressed(text = "Google Pixel 8a"))

            assertEquals(listOf(Effect.CopyText("Google Pixel 8a")), effects)
        }
    }

    @Test
    fun screenTapped_whileDetailsAreShown_hidesThemAndKeepsTheControls() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.DetailsShownChanged(isShown = true))
            viewModel.onAction(Action.ScreenTapped)

            assertFalse(viewModel.uiState.value.showsDetails)
            assertTrue(viewModel.uiState.value.chrome.showsBars)
        }
    }

    @Test
    fun zooming_hidesTheControlsUntilATap() {
        runTest {
            val viewModel = viewModel()

            zoomPage(viewModel = viewModel, page = 0, isZoomed = true)

            assertTrue(viewModel.uiState.value.isZoomed)
            assertFalse(viewModel.uiState.value.chrome.showsBars)

            zoomPage(viewModel = viewModel, page = 0, isZoomed = false)

            assertFalse(viewModel.uiState.value.chrome.showsBars)

            viewModel.onAction(Action.ScreenTapped)

            assertTrue(viewModel.uiState.value.chrome.showsBars)
        }
    }

    @Test
    fun anUnchangedZoom_keepsTheControlsAsTheyAre() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.ScreenTapped)
            zoomPage(viewModel = viewModel, page = 0, isZoomed = false)

            assertFalse(viewModel.uiState.value.chrome.showsBars)
        }
    }

    @Test
    fun anotherPagesZoom_keepsTheCurrentPageZoomed() {
        runTest {
            val viewModel = viewModel()

            zoomPage(viewModel = viewModel, page = 0, isZoomed = true)
            zoomPage(viewModel = viewModel, page = 1, isZoomed = false)

            assertTrue(viewModel.uiState.value.isZoomed)
        }
    }

    @Test
    fun aSelectedPage_showsFullPreviewsNearbyAndThumbnailsFurtherOut() {
        runTest {
            val items = (1..9).map { day -> image("2023101${day}_120000_000") }
            storedItems = items
            val viewModel = viewModel()

            viewModel.onAction(Action.PageSelected(page = 7))

            val pager = viewModel.uiState.value.pager
            val shown = pager.pages.map { page ->
                (pager.previews[page.key] as? GalleryPreview.Loaded)?.image?.asAndroidBitmap()
            }

            assertEquals(listOf(null), shown.take(2).distinct())
            assertEquals(listOf(THUMBNAIL), shown.subList(2, 6).distinct())
            assertEquals(listOf(PREVIEW), shown.drop(6).distinct())
        }
    }

    @Test
    fun aPageStillMovingInto_loadsNoNewPreviewsUntilItSettles() {
        runTest {
            val items = (1..9).map { day -> image("2023101${day}_120000_000") }
            storedItems = items
            val viewModel = viewModel()

            viewModel.onAction(Action.PageSettled(page = 0))
            viewModel.onAction(Action.PageSelected(page = 7))

            val pager = viewModel.uiState.value.pager
            val lastPage = pager.pages.last().key

            assertNull(pager.previews[lastPage])

            viewModel.onAction(Action.PageSettled(page = 7))

            val settledPager = viewModel.uiState.value.pager
            val lastPreview = settledPager.previews[lastPage] as? GalleryPreview.Loaded

            assertEquals(PREVIEW, lastPreview?.image?.asAndroidBitmap())
        }
    }

    private fun zoomPage(
        viewModel: GalleryViewModel,
        page: Int,
        isZoomed: Boolean,
    ) {
        viewModel.onAction(
            Action.ZoomChanged(
                pageKey = viewModel.uiState.value.pager.pages[page].key,
                isZoomed = isZoomed,
            ),
        )
    }

    private fun detailsAt(location: GeoCoordinates?): CapturedItemDetails {
        return mockk { every { this@mockk.location } returns location }
    }

    private fun detailsOf(
        viewModel: GalleryViewModel,
        page: Int,
    ): GalleryDetails {
        val uiState = viewModel.uiState.value

        return uiState.pager.details[uiState.pager.pages[page].key] ?: GalleryDetails.Loading
    }

    private fun pageKeys(viewModel: GalleryViewModel): List<String> {
        return viewModel.uiState.value.pager.pages.map { it.key }
    }

    private fun keysOf(vararg items: CapturedItem): List<String> {
        return items.map { it.uri.toString() }
    }

    private fun stallTheScan() {
        coEvery { capturedItemRepository.capturedItems() } coAnswers { awaitCancellation() }
    }

    private fun TestScope.effectsOf(viewModel: GalleryViewModel): List<Effect> {
        val effects = mutableListOf<Effect>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.effects.toList(effects)
        }
        return effects
    }

    private fun viewModel(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
        lastCapturedItem: CapturedItem? = null,
        arguments: SavedStateHandle = savedStateOf(lastCapturedItem),
    ): GalleryViewModel {
        return GalleryViewModel(
            arguments = arguments,
            entryPoint = entryPoint,
            systemGalleryLauncher = mockk(relaxed = true),
            observeGalleryItems = ObserveGalleryItemsImpl(
                capturedItemRepository = capturedItemRepository,
                galleryItemsMapper = GalleryItemsMapperImpl(),
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            ),
            previewsDelegate = GalleryPreviewsDelegateImpl(capturedItemRepository),
            detailsDelegate = GalleryDetailsDelegateImpl(
                arguments = arguments,
                capturedItemDetailsRepository = capturedItemDetailsRepository,
            ),
            deletionDelegate = GalleryDeletionDelegateImpl(
                trashCapturedItem = TrashCapturedItemImpl(capturedItemTrashRepository),
                restoreCapturedItem = RestoreCapturedItemImpl(capturedItemTrashRepository),
                deleteTrashedItem = DeleteTrashedItemImpl(capturedItemTrashRepository),
                deleteCapturedItem = deleteCapturedItem,
                applicationScope = CoroutineScope(mainDispatcherRule.testDispatcher),
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            ),
            playbackDelegate = mockk(relaxed = true),
            uiStateMapper = GalleryUiStateMapperImpl(captureTimeMapper = CaptureTimeMapperImpl()),
        )
    }

    private fun savedStateOf(lastCapturedItem: CapturedItem?): SavedStateHandle {
        val arguments = galleryArguments(
            sessionItems = null,
            lastCapturedItem = lastCapturedItem,
        )

        return SavedStateHandle(
            arguments.keySet().associateWith { key ->
                @Suppress("DEPRECATION")
                arguments.get(key)
            },
        )
    }

    private companion object {
        val THUMBNAIL = mockk<Bitmap>(relaxed = true)
        val PREVIEW = mockk<Bitmap>(relaxed = true)
        val STORAGE_FAILURE = Result.failure<Unit>(IOException())
        val NEWEST = image("20231016_120000_000")
        val OLDER = image("20231015_120000_000")
        val VIDEO = CapturedItem(
            type = CapturedItemType.VIDEO,
            dateString = "20231014_120000_000",
            uri = Uri.parse("content://media/external/video/media/20231014_120000_000"),
        )
        val LOCATION = GeoCoordinates(
            latitude = 47.5,
            longitude = -122.25,
        )

        fun image(dateString: String): CapturedItem {
            return CapturedItem(
                type = CapturedItemType.IMAGE,
                dateString = dateString,
                uri = Uri.parse("content://media/external/images/media/$dateString"),
            )
        }
    }
}
