package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.Os
import androidx.core.net.toUri
import app.grapheneos.camera.VIDEO_NAME_PREFIX
import app.grapheneos.camera.data.media.store.imageCollectionUri
import app.grapheneos.camera.data.media.store.videoCollectionUri
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.util.getTreeDocumentUri
import app.grapheneos.camera.util.removePendingFlagFromUri
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface CaptureOutputRepository {

    @Throws(IOException::class)
    suspend fun createImage(
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): Uri

    @Throws(IOException::class)
    suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    )

    @Throws(IOException::class)
    suspend fun publish(uri: Uri)

    @Throws(IOException::class)
    suspend fun delete(uri: Uri)

    @Throws(IOException::class)
    suspend fun deleteStalePendingVideos(olderThan: Duration)

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
        val uri = onStorage {
            when (storageLocation) {
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
        }

        return uri ?: throw IOException("unable to create $fileName in $storageLocation")
    }

    override suspend fun write(uri: Uri, bytes: ByteArray) {
        onStorage {
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

        onStorage {
            removePendingFlagFromUri(contentResolver, uri)
        }
    }

    override suspend fun delete(uri: Uri) {
        val deletedRows = onStorage {
            contentResolver.delete(uri, null, null)
        }

        if (deletedRows != 1) {
            throw IOException("unexpected number of deleted rows: $deletedRows")
        }
    }

    override suspend fun deleteStalePendingVideos(olderThan: Duration) {
        val selection = "${MediaStore.MediaColumns.IS_PENDING} = 1" +
            " AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" +
            " AND ${MediaStore.MediaColumns.DATE_ADDED} < ?"
        val cutoffSeconds = (System.currentTimeMillis() - olderThan.inWholeMilliseconds) / 1000L
        val arguments = arrayOf("$VIDEO_NAME_PREFIX%", cutoffSeconds.toString())

        onStorage {
            // Pending rows are filtered out of every operation unless they are explicitly asked for.
            @Suppress("DEPRECATION")
            val collection = MediaStore.setIncludePending(videoCollectionUri)

            contentResolver.delete(collection, selection, arguments)
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

    private suspend fun <T> onStorage(block: () -> T): T {
        return withContext(ioDispatcher) {
            val failure = try {
                return@withContext block()
            } catch (exception: ErrnoException) {
                exception
            } catch (exception: SecurityException) {
                exception
            } catch (exception: IllegalArgumentException) {
                exception
            } catch (exception: IllegalStateException) {
                exception
            } catch (exception: UnsupportedOperationException) {
                exception
            }

            throw IOException(failure)
        }
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
