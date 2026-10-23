package app.grapheneos.camera.domain.gallery.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.domain.gallery.model.CapturedItemDeletion
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeleteTrashedItemTest {

    private val repository = mockk<CapturedItemTrashRepository>()
    private val deleteTrashedItem: DeleteTrashedItem = DeleteTrashedItemImpl(repository)
    private val item = CapturedItem(
        type = CapturedItemType.IMAGE,
        dateString = "20231015_123456_789",
        uri = Uri.parse("content://media/external/images/media/1"),
    )

    @Test
    fun anItemTheStorageDeletes_isReportedDeleted() {
        runTest {
            coEvery { repository.delete(item) } returns Result.success(Unit)

            assertEquals(CapturedItemDeletion.Deleted, deleteTrashedItem(item))
        }
    }

    @Test
    fun anItemTheStorageRefuses_isReportedFailed() {
        runTest {
            coEvery { repository.delete(item) } returns Result.failure(IOException())

            assertEquals(CapturedItemDeletion.Failed, deleteTrashedItem(item))
        }
    }
}
