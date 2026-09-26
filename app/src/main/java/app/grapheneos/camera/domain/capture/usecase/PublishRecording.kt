package app.grapheneos.camera.domain.capture.usecase

import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import javax.inject.Inject

interface PublishRecording {
    suspend operator fun invoke(output: RecordingOutput): Boolean
}

internal class PublishRecordingImpl @Inject constructor(
    private val captureOutputRepository: CaptureOutputRepository,
) : PublishRecording {

    override suspend fun invoke(output: RecordingOutput): Boolean {
        if (!output.isPendingMediaStoreUri) {
            return true
        }

        return captureOutputRepository.publish(output.uri) !is CaptureOutputResult.Failure
    }
}
