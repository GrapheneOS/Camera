package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.runStorageCall
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.di.core.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CapturedItemTrashRepository {

    override fun canTrash(item: CapturedItem): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            item.uri.host == MediaStore.AUTHORITY
    }

    // The record is written before the item is trashed and dropped before it is restored, so a
    // process that dies in between leaves at worst a record of an item the user deleted, never one
    // of an item they kept.
    override suspend fun trash(item: CapturedItem): Result<Unit> {
        record(item.uri)

        return onStorage { setTrashed(uri = item.uri, isTrashed = true) }
            .onFailure { forget(item.uri) }
    }

    override suspend fun restore(item: CapturedItem): Result<Unit> {
        forget(item.uri)

        return onStorage { setTrashed(uri = item.uri, isTrashed = false) }
            .onFailure { record(item.uri) }
    }

    override suspend fun delete(item: CapturedItem): Result<Unit> {
        return onStorage<Unit> { contentResolver.delete(item.uri, null, null) }
            .onSuccess { forget(item.uri) }
    }

    override suspend fun deleteLeftovers() {
        val uris = mediaPrefs.data.first().trashedItems

        uris.forEach { uri ->
            onStorage { contentResolver.delete(uri.toUri(), null, null) }
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
    }
}
