package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import javax.inject.Inject

interface RevertToMediaStoreLocation {
    suspend operator fun invoke()
}

internal class RevertToMediaStoreLocationImpl @Inject constructor(
    private val capturedItemRepository: CapturedItemRepository,
) : RevertToMediaStoreLocation {

    override suspend fun invoke() {
        capturedItemRepository.setStorageLocation(CapturedItemRepository.MEDIA_STORE_LOCATION)
        capturedItemRepository.releaseUntrackedSafTrees()
    }
}
