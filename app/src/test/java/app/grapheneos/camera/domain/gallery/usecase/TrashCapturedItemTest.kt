package app.grapheneos.camera.domain.gallery.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.domain.gallery.model.CapturedItemTrashing
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrashCapturedItemTest {

    private val repository = mockk<CapturedItemTrashRepository> {
        every { canTrash(any()) } returns true
    }
    private val trashCapturedItem: TrashCapturedItem = TrashCapturedItemImpl(repository)
    private val item = CapturedItem(
        type = CapturedItemType.IMAGE,
        dateString = "20231015_123456_789",
        uri = Uri.parse("content://media/external/images/media/1"),
    )

    @Test
    fun anItemTheStorageTrashes_isReportedTrashed() {
        runTest {
            coEvery { repository.trash(item) } returns Result.success(Unit)

            assertEquals(CapturedItemTrashing.Trashed, trashCapturedItem(item))
        }
    }

    @Test
    fun anItemTheStorageRefuses_isReportedFailed() {
        runTest {
            coEvery { repository.trash(item) } returns Result.failure(IOException())

            assertEquals(CapturedItemTrashing.Failed, trashCapturedItem(item))
        }
    }

    @Test
    fun anItemWithoutATrash_isLeftAlone() {
        runTest {
            every { repository.canTrash(item) } returns false

            assertEquals(CapturedItemTrashing.NotTrashable, trashCapturedItem(item))
            coVerify(exactly = 0) { repository.trash(any()) }
        }
    }
}
