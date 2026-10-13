package app.grapheneos.camera.data.media.repository

import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import app.grapheneos.camera.data.media.model.StorageSpace
import app.grapheneos.camera.di.core.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface StorageSpaceRepository {

    /** Null when [location] is not on the device's own storage, or its space is unknown. */
    suspend fun storageSpace(location: String): StorageSpace?
}

internal class StorageSpaceRepositoryImpl @Inject constructor(
    private val storageStatsManager: StorageStatsManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StorageSpaceRepository {

    override suspend fun storageSpace(location: String): StorageSpace? {
        if (!isOnPrimaryVolume(location)) return null

        return withContext(ioDispatcher) {
            try {
                StorageSpace(
                    freeBytes = storageStatsManager.getFreeBytes(StorageManager.UUID_DEFAULT),
                    totalBytes = storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT),
                )
            } catch (exception: IOException) {
                Log.w(TAG, "Unable to read the space of the device's storage", exception)
                null
            }
        }
    }

    private fun isOnPrimaryVolume(location: String): Boolean {
        val uri = location.toUri()

        return when {
            location == CapturedItemRepository.MEDIA_STORE_LOCATION -> true
            uri.authority != EXTERNAL_STORAGE_AUTHORITY -> false
            !DocumentsContract.isTreeUri(uri) -> false
            else -> DocumentsContract.getTreeDocumentId(uri).startsWith(PRIMARY_VOLUME_PREFIX)
        }
    }

    private companion object {
        const val TAG = "StorageSpaceRepository"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val PRIMARY_VOLUME_PREFIX = "primary:"
    }
}
