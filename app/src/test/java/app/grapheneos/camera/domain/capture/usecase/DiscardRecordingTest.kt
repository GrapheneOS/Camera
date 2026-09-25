package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.testutil.recordingOutput
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscardRecordingTest {

    private val repository = mockk<CaptureOutputRepository>(relaxed = true)

    private val discardRecording = DiscardRecordingImpl(repository)

    @Test
    fun invoke_anotherAppsFile_isLeftAlone() {
        runTest {
            discardRecording(recordingOutput(uri = OWN_URI, isOwnFile = false))

            coVerify(exactly = 0) { repository.delete(any()) }
        }
    }

    @Test
    fun invoke_ourOwnFile_isDeleted() {
        runTest {
            discardRecording(recordingOutput(uri = OWN_URI, isOwnFile = true))

            coVerify(exactly = 1) { repository.delete(OWN_URI) }
        }
    }

    private companion object {
        val OWN_URI: Uri = Uri.parse("content://media/external_primary/video/media/1")
    }
}
