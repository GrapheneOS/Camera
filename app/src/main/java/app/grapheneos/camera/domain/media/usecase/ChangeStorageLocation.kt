package app.grapheneos.camera.domain.media.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.media.model.StorageLocationChange
import javax.inject.Inject

interface ChangeStorageLocation {
    suspend operator fun invoke(tree: Uri): StorageLocationChange
}

internal class ChangeStorageLocationImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
) : ChangeStorageLocation {

    override suspend fun invoke(tree: Uri): StorageLocationChange {
        return when {
            capturedItemRepository.canStoreSafTree(tree) -> {
                val location = tree.toString()
                capturedItemRepository.persistSafTreeAccess(tree)
                capturedItemRepository.setStorageLocation(location)
                capturedItemRepository.releaseUntrackedSafTrees()

                StorageLocationChange.Changed(location)
            }

            else -> StorageLocationChange.Rejected
        }
    }
}
