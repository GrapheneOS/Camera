package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.domain.gallery.model.CapturedItemDeletion
import javax.inject.Inject

internal interface DeleteTrashedItem {
    suspend operator fun invoke(item: CapturedItem): CapturedItemDeletion
}

internal class DeleteTrashedItemImpl @Inject constructor(
    private val capturedItemTrashRepository: CapturedItemTrashRepository,
) : DeleteTrashedItem {

    override suspend fun invoke(item: CapturedItem): CapturedItemDeletion {
        return when {
            capturedItemTrashRepository.delete(item).isSuccess -> CapturedItemDeletion.Deleted
            else -> CapturedItemDeletion.Failed
        }
    }
}
