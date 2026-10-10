package app.grapheneos.camera.domain.media.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.media.model.StorageLocationChange
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChangeStorageLocationTest {

    private val repository = mockk<CapturedItemRepository>(relaxed = true)
    private val changeStorageLocation = ChangeStorageLocationImpl(repository)
    private val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary")

    @Test
    fun aTreeItCanKeep_becomesTheLocationBeforeOlderGrantsAreReleased() {
        runTest {
            every { repository.canStoreSafTree(tree) } returns true

            val change = changeStorageLocation(tree)

            assertEquals(StorageLocationChange.Changed(tree.toString()), change)
            coVerifyOrder {
                repository.persistSafTreeAccess(tree)
                repository.setStorageLocation(tree.toString())
                repository.releaseUntrackedSafTrees()
            }
        }
    }

    @Test
    fun aTreeItCannotKeep_leavesTheLocationAlone() {
        runTest {
            every { repository.canStoreSafTree(tree) } returns false

            val change = changeStorageLocation(tree)

            assertEquals(StorageLocationChange.Rejected, change)
            coVerify(exactly = 0) { repository.persistSafTreeAccess(any()) }
            coVerify(exactly = 0) { repository.setStorageLocation(any()) }
            coVerify(exactly = 0) { repository.releaseUntrackedSafTrees() }
        }
    }
}
