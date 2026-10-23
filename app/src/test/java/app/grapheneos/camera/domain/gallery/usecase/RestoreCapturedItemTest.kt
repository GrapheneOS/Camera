package app.grapheneos.camera.domain.gallery.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.domain.gallery.model.CapturedItemRestoring
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreCapturedItemTest {

    private val repository = mockk<CapturedItemTrashRepository>()
    private val restoreCapturedItem: RestoreCapturedItem = RestoreCapturedItemImpl(repository)
    private val item = CapturedItem(
        type = CapturedItemType.IMAGE,
        dateString = "20231015_123456_789",
        uri = Uri.parse("content://media/external/images/media/1"),
    )

    @Test
    fun anItemTheStorageRestores_isReportedRestored() {
        runTest {
            coEvery { repository.restore(item) } returns Result.success(Unit)

            assertEquals(CapturedItemRestoring.Restored, restoreCapturedItem(item))
        }
    }

    @Test
    fun anItemTheStorageRefuses_isReportedFailed() {
        runTest {
            coEvery { repository.restore(item) } returns Result.failure(IOException())

            assertEquals(CapturedItemRestoring.Failed, restoreCapturedItem(item))
        }
    }
}
