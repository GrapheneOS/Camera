package app.grapheneos.camerax

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import androidx.camera.core.ImageCapture
import java.io.OutputStream

sealed class OutputFileOptions(
    open var metadata: ImageCapture.Metadata = ImageCapture.Metadata()
) {

    data class OutputFileOptionsMediaStore(
        val contentResolver: ContentResolver,
        val saveCollection: Uri,
        val contentValues: ContentValues,
        override var metadata: ImageCapture.Metadata = ImageCapture.Metadata()
    ) : OutputFileOptions(metadata)

    data class OutputFileOptionsOutputStream(
        val outputStream: OutputStream,
        override var metadata: ImageCapture.Metadata = ImageCapture.Metadata()
    ) : OutputFileOptions(metadata)


}