package app.grapheneos.camera.domain.capture

import android.graphics.Bitmap
import android.net.Uri
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedPreviewImpl
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoreCapturedPreviewTest {

    private val repository = mockk<CaptureOutputRepository> {
        coEvery { write(any(), any()) } returns CaptureOutputResult.Success(Unit)
    }

    private val bitmap = mockk<Bitmap>(relaxed = true)

    private val storeCapturedPreview = StoreCapturedPreviewImpl(repository)

    @Test
    fun invoke_intoAPngFile_writesPng() {
        runTest {
            val stored = storeCapturedPreview(uri = uri("shot.png"), bitmap = bitmap)

            assertTrue(stored)
            verify(exactly = 1) {
                bitmap.compress(Bitmap.CompressFormat.PNG, FULL_QUALITY, any())
            }
        }
    }

    @Test
    fun invoke_intoAnyOtherFile_writesJpeg() {
        runTest {
            storeCapturedPreview(uri = uri("shot"), bitmap = bitmap)

            verify(exactly = 1) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, FULL_QUALITY, any())
            }
        }
    }

    @Test
    fun invoke_whenTheFileCannotBeWritten_reportsIt() {
        runTest {
            coEvery { repository.write(any(), any()) } returns
                CaptureOutputResult.Failure(IOException("gone"))

            val stored = storeCapturedPreview(uri = uri("shot.jpg"), bitmap = bitmap)

            assertFalse(stored)
        }
    }

    private fun uri(fileName: String): Uri {
        return Uri.parse("content://com.example.app/$fileName")
    }

    private companion object {
        const val FULL_QUALITY = 100
    }
}
