package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.domain.gallery.model.CapturedItemRestoring
import javax.inject.Inject

internal interface RestoreCapturedItem {
    suspend operator fun invoke(item: CapturedItem): CapturedItemRestoring
}

internal class RestoreCapturedItemImpl @Inject constructor(
    private val capturedItemTrashRepository: CapturedItemTrashRepository,
) : RestoreCapturedItem {

    override suspend fun invoke(item: CapturedItem): CapturedItemRestoring {
        return when {
            capturedItemTrashRepository.restore(item).isSuccess -> CapturedItemRestoring.Restored
            else -> CapturedItemRestoring.Failed
        }
    }
}
