package app.grapheneos.camera.domain.gallery.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
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
class DeleteCapturedItemTest {

    private val repository = mockk<CaptureOutputRepository>()
    private val deleteCapturedItem = DeleteCapturedItemImpl(repository)
    private val item = CapturedItem(
        type = CapturedItemType.IMAGE,
        dateString = "20231015_123456_789",
        uri = Uri.parse("content://media/external/images/media/1"),
    )

    @Test
    fun anItemTheStorageDeletes_isReportedDeleted() {
        runTest {
            coEvery { repository.delete(item.uri) } returns Result.success(Unit)

            assertEquals(CapturedItemDeletion.Deleted, deleteCapturedItem(item))
        }
    }

    @Test
    fun anItemTheStorageRefuses_isReportedFailed() {
        runTest {
            coEvery { repository.delete(item.uri) } returns Result.failure(IOException())

            assertEquals(CapturedItemDeletion.Failed, deleteCapturedItem(item))
        }
    }
}
