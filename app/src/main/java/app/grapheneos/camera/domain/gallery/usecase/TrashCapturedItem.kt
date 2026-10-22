package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.domain.gallery.model.CapturedItemTrashing
import javax.inject.Inject

internal interface TrashCapturedItem {
    suspend operator fun invoke(item: CapturedItem): CapturedItemTrashing
}

internal class TrashCapturedItemImpl @Inject constructor(
    private val capturedItemTrashRepository: CapturedItemTrashRepository,
) : TrashCapturedItem {

    override suspend fun invoke(item: CapturedItem): CapturedItemTrashing {
        return when {
            !capturedItemTrashRepository.canTrash(item) -> CapturedItemTrashing.NotTrashable
            capturedItemTrashRepository.trash(item).isSuccess -> CapturedItemTrashing.Trashed
            else -> CapturedItemTrashing.Failed
        }
    }
}
