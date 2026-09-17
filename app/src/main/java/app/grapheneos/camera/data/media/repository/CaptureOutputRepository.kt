package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import androidx.core.net.toUri
import app.grapheneos.camera.data.media.store.imageCollectionUri
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.util.getTreeDocumentUri
import app.grapheneos.camera.util.removePendingFlagFromUri
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface CaptureOutputRepository {

    suspend fun createImage(
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): Uri

    suspend fun write(uri: Uri, bytes: ByteArray)

    suspend fun publish(uri: Uri)

    suspend fun delete(uri: Uri)

    companion object {
        const val DEFAULT_MEDIA_STORE_CAPTURE_PATH = "DCIM/Camera"

        // see com.android.externalstorage.ExternalStorageProvider and
        // com.android.internal.content.FileSystemProvider
        const val SAF_URI_HOST_EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    }
}

internal class CaptureOutputRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CaptureOutputRepository {

    override suspend fun createImage(
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): Uri {
        return withContext(ioDispatcher) {
            val uri = when (storageLocation) {
                CapturedItemRepository.MEDIA_STORE_LOCATION -> insertPendingImage(
                    fileName = fileName,
                    mimeType = mimeType,
                )

                else -> DocumentsContract.createDocument(
                    contentResolver,
                    getTreeDocumentUri(storageLocation.toUri()),
                    mimeType,
                    fileName,
                )
            }

            uri ?: throw IOException("unable to create $fileName in $storageLocation")
        }
    }

    override suspend fun write(uri: Uri, bytes: ByteArray) {
        withContext(ioDispatcher) {
            val descriptor = contentResolver.openAssetFileDescriptor(uri, "w")
                ?: throw IOException("unable to open $uri")

            descriptor.use {
                val fd = it.fileDescriptor
                var offset = 0

                do {
                    // "-1" is never returned to indicate an error, ErrnoException is thrown instead
                    offset += Os.write(fd, bytes, offset, bytes.size - offset)
                } while (offset != bytes.size)

                if (shouldFsync(uri)) {
                    Os.fsync(fd)
                }
            }
        }
    }

    override suspend fun publish(uri: Uri) {
        if (uri.host != MediaStore.AUTHORITY) return

        withContext(ioDispatcher) {
            removePendingFlagFromUri(contentResolver, uri)
        }
    }

    override suspend fun delete(uri: Uri) {
        withContext(ioDispatcher) {
            val deletedRows = contentResolver.delete(uri, null, null)
            check(deletedRows == 1) { "unexpected number of deleted rows: $deletedRows" }
        }
    }

    private fun insertPendingImage(fileName: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                CaptureOutputRepository.DEFAULT_MEDIA_STORE_CAPTURE_PATH,
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return contentResolver.insert(imageCollectionUri, values)
    }

    private fun shouldFsync(uri: Uri): Boolean {
        return when (uri.host) {
            MediaStore.AUTHORITY,
            CaptureOutputRepository.SAF_URI_HOST_EXTERNAL_STORAGE,
            -> true

            else -> false
        }
    }
}
