package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.testutil.recordingOutput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PublishRecordingTest {

    private val repository = mockk<CaptureOutputRepository>(relaxed = true)

    private val publishRecording = PublishRecordingImpl(repository)

    @Test
    fun invoke_aRecordingThatIsNotAPendingRow_isAlreadyPublished() {
        runTest {
            val output = recordingOutput(uri = OWN_URI, isPendingMediaStoreUri = false)

            assertTrue(publishRecording(output))
            coVerify(exactly = 0) { repository.publish(any()) }
        }
    }

    @Test
    fun invoke_aPendingRowThatCannotBePublished_reportsTheFailure() {
        runTest {
            coEvery { repository.publish(OWN_URI) } returns CaptureOutputResult.Failure(
                IOException("gone"),
            )
            val output = recordingOutput(uri = OWN_URI, isPendingMediaStoreUri = true)

            assertFalse(publishRecording(output))
        }
    }

    private companion object {
        val OWN_URI: Uri = Uri.parse("content://media/external_primary/video/media/1")
    }
}
