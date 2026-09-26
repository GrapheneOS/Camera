package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.core.net.toUri
import app.grapheneos.camera.VIDEO_NAME_PREFIX
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.model.MEDIA_STORE_CAPTURE_PATH
import app.grapheneos.camera.data.media.model.SAF_URI_HOST_EXTERNAL_STORAGE
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

    suspend fun createImage(
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): CaptureOutputResult<Uri>

    suspend fun createVideo(
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): CaptureOutputResult<Uri>

    suspend fun openForWriting(
        uri: Uri,
    ): CaptureOutputResult<ParcelFileDescriptor>

    suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): CaptureOutputResult<Unit>

    suspend fun publish(uri: Uri): CaptureOutputResult<Unit>

    suspend fun delete(uri: Uri)

    suspend fun deleteStalePendingVideos(olderThan: Duration)
}

internal class CaptureOutputRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CaptureOutputRepository {

    override suspend fun createImage(
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): CaptureOutputResult<Uri> {
        return create(
            collection = imageCollectionUri,
            storageLocation = storageLocation,
            fileName = fileName,
            mimeType = mimeType,
        )
    }

    override suspend fun createVideo(
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): CaptureOutputResult<Uri> {
        return create(
            collection = videoCollectionUri,
            storageLocation = storageLocation,
            fileName = fileName,
            mimeType = mimeType,
        )
    }

    override suspend fun openForWriting(uri: Uri): CaptureOutputResult<ParcelFileDescriptor> {
        return onStorage {
            contentResolver.openFileDescriptor(uri, "w")
                ?: throw IOException("unable to open $uri")
        }
    }

    override suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): CaptureOutputResult<Unit> {
        return onStorage {
            val outputDescriptor = contentResolver.openAssetFileDescriptor(uri, "w")
                ?: throw IOException("unable to open $uri")

            outputDescriptor.use { descriptor ->
                val fileDescriptor = descriptor.fileDescriptor

                descriptor.createOutputStream().use { outputStream ->
                    outputStream.write(bytes)
                    outputStream.flush()

                    if (shouldFsync(uri)) {
                        Os.fsync(fileDescriptor)
                    }
                }
            }
        }
    }

    override suspend fun publish(uri: Uri): CaptureOutputResult<Unit> {
        if (uri.host != MediaStore.AUTHORITY) {
            return CaptureOutputResult.Success(Unit)
        }

        return onStorage {
            removePendingFlagFromUri(contentResolver, uri)
        }
    }

    override suspend fun delete(uri: Uri) {
        onStorage {
            when (uri.host) {
                MediaStore.AUTHORITY -> {
                    val deletedRows = contentResolver.delete(uri, null, null)

                    if (deletedRows != 1) {
                        throw IOException("unexpected number of deleted rows: $deletedRows")
                    }
                }

                else -> {
                    val isDeleted = DocumentsContract.deleteDocument(contentResolver, uri)

                    if (!isDeleted) {
                        throw IOException("unable to delete the document $uri")
                    }
                }
            }
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

    private suspend fun create(
        collection: Uri,
        storageLocation: String,
        fileName: String,
        mimeType: String,
    ): CaptureOutputResult<Uri> {
        return onStorage {
            val uri = when (storageLocation) {
                CapturedItemRepository.MEDIA_STORE_LOCATION -> insertPending(
                    collection = collection,
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

    private fun insertPending(
        collection: Uri,
        fileName: String,
        mimeType: String,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, MEDIA_STORE_CAPTURE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        return contentResolver.insert(collection, values)
    }

    private suspend fun <T> onStorage(block: () -> T): CaptureOutputResult<T> {
        return withContext(ioDispatcher) {
            val failure = try {
                return@withContext CaptureOutputResult.Success(block())
            } catch (exception: ErrnoException) {
                exception
            } catch (exception: IOException) {
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

            Log.w(TAG, "the capture storage refused the request", failure)

            CaptureOutputResult.Failure(failure)
        }
    }

    private fun shouldFsync(uri: Uri): Boolean {
        return when (uri.host) {
            MediaStore.AUTHORITY -> true
            SAF_URI_HOST_EXTERNAL_STORAGE -> true
            else -> false
        }
    }

    private companion object {
        private const val TAG = "CaptureOutputRepository"
    }
}
