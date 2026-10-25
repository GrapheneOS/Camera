package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.util.asyncAndAwait
import app.grapheneos.camera.util.runStorageCall
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

internal interface CapturedItemTrashRepository {

    fun canTrash(item: CapturedItem): Boolean

    suspend fun trash(item: CapturedItem): Result<Unit>

    suspend fun restore(item: CapturedItem): Result<Unit>

    suspend fun delete(item: CapturedItem): Result<Unit>

    /** Deletes what was trashed but never restored or deleted, e.g. because the process died. */
    suspend fun deleteLeftovers()
}

internal class CapturedItemTrashRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val mediaPrefs: DataStore<MediaPrefs>,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CapturedItemTrashRepository {

    override fun canTrash(item: CapturedItem): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            item.uri.host == MediaStore.AUTHORITY
    }

    // Recorded before trashing and forgotten after restoring, on the application scope so that
    // closing the gallery cannot stop either halfway. If the process dies in between, the next
    // launch deletes the item only if it is still in the trash.
    override suspend fun trash(item: CapturedItem): Result<Unit> {
        return applicationScope.asyncAndAwait(ioDispatcher) {
            record(item.uri)

            onStorage { setTrashed(uri = item.uri, isTrashed = true) }
                .onFailure { forget(item.uri) }
        }
    }

    override suspend fun restore(item: CapturedItem): Result<Unit> {
        return applicationScope.asyncAndAwait(ioDispatcher) {
            onStorage { setTrashed(uri = item.uri, isTrashed = false) }
                .onSuccess { forget(item.uri) }
        }
    }

    override suspend fun delete(item: CapturedItem): Result<Unit> {
        return onStorage<Unit> { contentResolver.delete(item.uri, null, null) }
            .onSuccess { forget(item.uri) }
    }

    override suspend fun deleteLeftovers() {
        val uris = mediaPrefs.data.first().trashedItems

        uris.forEach { uri ->
            onStorage { contentResolver.delete(uri.toUri(), STILL_TRASHED, null) }
        }
        mediaPrefs.updateData { it.copy(trashedItems = it.trashedItems - uris.toSet()) }
    }

    private fun setTrashed(
        uri: Uri,
        isTrashed: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw UnsupportedOperationException("MediaStore has no trash before Android 11")
        }

        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.IS_TRASHED,
                when {
                    isTrashed -> 1
                    else -> 0
                },
            )
        }
        val updatedRows = contentResolver.update(uri, values, null, null)

        if (updatedRows != 1) {
            throw IOException("unexpected number of updated rows: $updatedRows")
        }
    }

    private suspend fun record(uri: Uri) {
        mediaPrefs.updateData { it.copy(trashedItems = it.trashedItems + uri.toString()) }
    }

    private suspend fun forget(uri: Uri) {
        mediaPrefs.updateData { it.copy(trashedItems = it.trashedItems - uri.toString()) }
    }

    private suspend fun <T> onStorage(block: () -> T): Result<T> {
        return runStorageCall(
            dispatcher = ioDispatcher,
            tag = TAG,
            block = block,
        )
    }

    private companion object {
        const val TAG = "CapturedItemTrashRepository"
        const val STILL_TRASHED = "${MediaStore.MediaColumns.IS_TRASHED} = 1"
    }
}
