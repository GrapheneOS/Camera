package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import app.grapheneos.camera.BuildConfig
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.data.media.store.CapturedItemStore
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

interface CapturedItemRepository {

    fun lastCapturedItem(): CapturedItem?

    fun saveLastCapturedItem(item: CapturedItem)

    fun trackPreviousStorageLocation(treeUri: Uri)

    fun releaseUntrackedSafTrees()

    fun migrateStoredCaptures(onLastCapturedItem: (CapturedItem) -> Unit)

    @Throws(InterruptedException::class)
    fun capturedItems(): List<CapturedItem>
}

internal class CapturedItemRepositoryImpl @Inject constructor(
    private val store: CapturedItemStore,
    @ActivityContext private val context: Context,
) : CapturedItemRepository {

    override fun lastCapturedItem(): CapturedItem? {
        return store.readLastCapturedItem()
    }

    override fun saveLastCapturedItem(item: CapturedItem) {
        store.writeLastCapturedItem(item)
    }

    override fun trackPreviousStorageLocation(treeUri: Uri) {
        store.trackSafTree(treeUri)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun releaseUntrackedSafTrees() {
        val tracked = store.safTrees()
        val resolver = context.contentResolver

        resolver.persistedUriPermissions.forEach { permission ->
            val uri = permission.uri
            val flags = CapturedItems.safTreeFlagsToRelease(
                uri,
                permission.isReadPermission,
                permission.isWritePermission,
                tracked,
            )
            if (flags == 0) {
                return@forEach
            }

            try {
                resolver.releasePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(CapturedItems.TAG, "unable to release the grant for $uri", e)
                }
            }
        }
    }

    override fun migrateStoredCaptures(onLastCapturedItem: (CapturedItem) -> Unit) {
        store.migrateLastCapturedItem()

        val joinedUris = store.readLegacyMediaUris() ?: return

        store.replaceLegacyMediaUris(
            legacyTrees(joinedUris = joinedUris, onLastCapturedItem = onLastCapturedItem),
        )
    }

    override fun capturedItems(): List<CapturedItem> {
        val resolver = context.contentResolver
        val items = ArrayList<CapturedItem>()

        collectMediaStoreItems(resolver, MediaStore.VOLUME_EXTERNAL_PRIMARY, items)

        store.safTrees().forEach {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            collectSafItems(resolver, it, items)
        }

        return items.distinct()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun collectMediaStoreItems(
        resolver: ContentResolver,
        volumeName: String,
        dest: ArrayList<CapturedItem>,
    ) {
        val volumeUri = MediaStore.Files.getContentUri(volumeName)
        val columns = arrayOf(BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val idColumn = 0
        val nameColumn = 1

        try {
            resolver.query(volumeUri, columns, null, null)?.use {
                dest.ensureCapacity(it.count)

                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)
                    val uri = ContentUris.withAppendedId(volumeUri, it.getLong(idColumn))

                    CapturedItems.parseCapturedItem(name, uri)?.let { item ->
                        dest.add(item)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(CapturedItems.TAG, "unable to collect MediaStore items, volume $volumeName", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun collectSafItems(
        resolver: ContentResolver,
        treeUri: Uri,
        dest: ArrayList<CapturedItem>,
    ) {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val childDocumentsUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val idColumn = 0
        val nameColumn = 1

        try {
            resolver.query(childDocumentsUri, columns, null, null)?.use {
                dest.ensureCapacity(it.count)

                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)
                    val id = it.getString(idColumn)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

                    CapturedItems.parseCapturedItem(name, uri)?.let { item ->
                        dest.add(item)
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(CapturedItems.TAG, "unable to collect SAF items, treeUri $treeUri", e)
            }
        }
    }

    /** Unbounded, unlike the tracked list: a tree left out here loses its grant for good. */
    private fun legacyTrees(
        joinedUris: String,
        onLastCapturedItem: (CapturedItem) -> Unit,
    ): List<Uri> {
        val currentTreeUri = store.currentSafTree()
        val trees = ArrayList<Uri>()
        var checkedLastCapturedItem = false

        joinedUris.split(LEGACY_MEDIA_URI_SEPARATOR).forEach { uriString ->
            val uri = uriString.toUri()
            val authority = uri.authority ?: return@forEach

            if (!checkedLastCapturedItem) {
                reportLastCapturedItem(uri, authority, onLastCapturedItem)
                checkedLastCapturedItem = true
            }

            if (authority == MediaStore.AUTHORITY) {
                return@forEach
            }

            val treeUri = DocumentsContract.buildTreeDocumentUri(
                authority,
                DocumentsContract.getTreeDocumentId(uri),
            )

            val skip = treeUri == currentTreeUri ||
                trees.contains(treeUri) ||
                treeUri.toString().contains(CapturedItems.SAF_TREE_SEPARATOR)

            if (skip) {
                return@forEach
            }

            trees.add(treeUri)
        }

        return trees
    }

    private fun reportLastCapturedItem(
        uri: Uri,
        authority: String,
        onLastCapturedItem: (CapturedItem) -> Unit,
    ) {
        val columnName = when (authority) {
            MediaStore.AUTHORITY -> MediaStore.MediaColumns.DISPLAY_NAME
            else -> DocumentsContract.Document.COLUMN_DISPLAY_NAME
        }

        var fileName: String? = null

        try {
            context.contentResolver.query(uri, arrayOf(columnName), null, null)?.use {
                if (it.moveToFirst()) {
                    fileName = it.getString(0)
                }
            }
        } catch (_: Exception) {
        }

        fileName?.let { name ->
            CapturedItems.parseCapturedItem(name, uri)?.let(onLastCapturedItem)
        }
    }

    companion object {
        private const val LEGACY_MEDIA_URI_SEPARATOR = ";"
    }
}

internal class LockscreenCapturedItemRepository(
    private val delegate: CapturedItemRepository,
) : CapturedItemRepository by delegate {

    @Suppress("EmptyFunctionBlock")
    override fun releaseUntrackedSafTrees() {
    }
}
