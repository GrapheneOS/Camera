package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.domain.gallery.model.CapturedItemDeletion
import javax.inject.Inject

interface DeleteCapturedItem {
    suspend operator fun invoke(item: CapturedItem): CapturedItemDeletion
}

internal class DeleteCapturedItemImpl @Inject constructor(
    private val captureOutputRepository: CaptureOutputRepository,
) : DeleteCapturedItem {

    override suspend fun invoke(item: CapturedItem): CapturedItemDeletion {
        return when {
            captureOutputRepository.delete(item.uri).isSuccess -> CapturedItemDeletion.Deleted
            else -> CapturedItemDeletion.Failed
        }
    }
}
