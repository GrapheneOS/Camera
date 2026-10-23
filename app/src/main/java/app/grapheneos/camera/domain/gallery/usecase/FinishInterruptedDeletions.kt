package app.grapheneos.camera.domain.gallery.usecase

import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import javax.inject.Inject

internal interface FinishInterruptedDeletions {
    suspend operator fun invoke()
}

// A deleted item stays in the MediaStore trash while its deletion can be undone; if the process
// dies before then, the deletion is finished on the next launch.
internal class FinishInterruptedDeletionsImpl @Inject constructor(
    private val capturedItemTrashRepository: CapturedItemTrashRepository,
) : FinishInterruptedDeletions {

    override suspend fun invoke() {
        capturedItemTrashRepository.deleteLeftovers()
    }
}
