package app.grapheneos.camera.data.media.repository

import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import android.util.Log
import app.grapheneos.camera.data.media.model.StorageFolder
import app.grapheneos.camera.data.media.model.StorageSpace
import app.grapheneos.camera.di.core.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

interface StorageSpaceRepository {

    /** Null when [folder] is not on the device's own storage, or its space is unknown. */
    suspend fun storageSpace(folder: StorageFolder): StorageSpace?
}

internal class StorageSpaceRepositoryImpl @Inject constructor(
    private val storageStatsManager: StorageStatsManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StorageSpaceRepository {

    override suspend fun storageSpace(folder: StorageFolder): StorageSpace? {
        if (folder !is StorageFolder.MainStorage) return null

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

    private companion object {
        const val TAG = "StorageSpaceRepository"
    }
}
