package app.grapheneos.camera.domain.capture

import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.domain.capture.usecase.DeleteStalePendingRecordingsImpl
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeleteStalePendingRecordingsTest {

    private val repository = mockk<CaptureOutputRepository>(relaxed = true)

    private val deleteStalePendingRecordings = DeleteStalePendingRecordingsImpl(
        captureOutputRepository = repository,
    )

    @Test
    fun invoke_reapsRecordingsLeftPendingForAnHour() {
        runTest {
            deleteStalePendingRecordings()

            coVerify(exactly = 1) { repository.deleteStalePendingVideos(olderThan = 1.hours) }
        }
    }
}
