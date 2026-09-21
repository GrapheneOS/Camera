package app.grapheneos.camera.domain.capture.usecase

import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import javax.inject.Inject

interface DiscardRecording {
    suspend operator fun invoke(output: RecordingOutput)
}

internal class DiscardRecordingImpl @Inject constructor(
    private val captureOutputRepository: CaptureOutputRepository,
) : DiscardRecording {

    override suspend fun invoke(output: RecordingOutput) {
        if (!output.isOwnFile) {
            return
        }

        captureOutputRepository.delete(output.uri)
    }
}
