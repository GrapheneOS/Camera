package app.grapheneos.camera.domain.capture.usecase

import android.graphics.Bitmap
import android.net.Uri
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
        coEvery { write(any(), any()) } returns Result.success(Unit)
    }

    private val bitmap = mockk<Bitmap>(relaxed = true) {
        every { compress(any(), any(), any()) } returns true
    }

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
                Result.failure(IOException("gone"))

            val stored = storeCapturedPreview(uri = uri("shot.jpg"), bitmap = bitmap)

            assertFalse(stored)
        }
    }

    @Test
    fun invoke_whenTheBitmapCannotBeEncoded_leavesTheFileAlone() {
        runTest {
            every { bitmap.compress(any(), any(), any()) } returns false

            val stored = storeCapturedPreview(uri = uri("shot.jpg"), bitmap = bitmap)

            assertFalse(stored)
            coVerify(exactly = 0) { repository.write(any(), any()) }
        }
    }

    private fun uri(fileName: String): Uri {
        return Uri.parse("content://com.example.app/$fileName")
    }

    private companion object {
        const val FULL_QUALITY = 100
    }
}
