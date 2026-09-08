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
import androidx.datastore.core.DataStore
import app.grapheneos.camera.BuildConfig
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoredCapturedItem
import app.grapheneos.camera.di.core.IoDispatcher
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface CapturedItemRepository {

    val lastCapturedItem: Flow<CapturedItem?>

    suspend fun saveLastCapturedItem(item: CapturedItem)

    val storageLocation: Flow<String>

    suspend fun setStorageLocation(value: String)

    suspend fun releaseUntrackedSafTrees()

    suspend fun migrateStoredCaptures(): CapturedItem?

    suspend fun capturedItems(): List<CapturedItem>

    companion object {
        const val MEDIA_STORE_LOCATION = ""
    }
}

internal class CapturedItemRepositoryImpl @Inject constructor(
    private val storagePrefs: DataStore<StoragePrefs>,
    private val mediaPrefs: DataStore<MediaPrefs>,
    @ActivityContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CapturedItemRepository {

    override val lastCapturedItem: Flow<CapturedItem?> = mediaPrefs.data.map { prefs ->
        prefs.lastCapturedItem?.let { stored ->
            CapturedItem(
                type = stored.type,
                dateString = stored.dateString,
                uri = stored.uri.toUri(),
            )
        }
    }

    override suspend fun saveLastCapturedItem(item: CapturedItem) {
        mediaPrefs.updateData {
            it.copy(
                lastCapturedItem = StoredCapturedItem(
                    type = item.type,
                    dateString = item.dateString,
                    uri = item.uri.toString(),
                ),
            )
        }
    }

    override val storageLocation: Flow<String> = storagePrefs.data.map {
        it.storageLocation ?: CapturedItemRepository.MEDIA_STORE_LOCATION
    }

    override suspend fun setStorageLocation(value: String) {
        storagePrefs.updateData { prefs ->
            val previous = prefs.storageLocation?.takeIf { it.isNotEmpty() }

            when {
                previous == null || previous == value -> prefs.copy(storageLocation = value)
                else -> {
                    val trees = prefs.previousSafTrees.mapTo(ArrayList()) { it.toUri() }
                    val previousTree = previous.toUri()

                    trees.remove(previousTree)
                    trees.add(0, previousTree)

                    while (trees.size > CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES) {
                        // MutableList.removeLast() resolves to an API 35 Java method.
                        trees.removeAt(trees.lastIndex)
                    }

                    prefs.copy(
                        storageLocation = value,
                        previousSafTrees = trees.map { it.toString() },
                    )
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun releaseUntrackedSafTrees() {
        withContext(ioDispatcher) {
            val tracked = trackedSafTrees()
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
    }

    override suspend fun migrateStoredCaptures(): CapturedItem? {
        return withContext(ioDispatcher) {
            val joinedUris = storagePrefs.data.first().legacyMediaUris
                ?: return@withContext null
            val uris = joinedUris.split(LEGACY_MEDIA_URI_SEPARATOR).map { it.toUri() }

            val migrated = uris.firstOrNull { it.authority != null }?.let {
                legacyCapturedItem(it)
            }

            val trees = legacyTrees(uris)

            storagePrefs.updateData { prefs ->
                when {
                    trees.isEmpty() -> prefs.copy(legacyMediaUris = null)
                    else -> prefs.copy(
                        previousSafTrees = trees.map { it.toString() },
                        legacyMediaUris = null,
                    )
                }
            }

            migrated
        }
    }

    override suspend fun capturedItems(): List<CapturedItem> {
        val resolver = context.contentResolver
        val items = ArrayList<CapturedItem>()

        collectMediaStoreItems(resolver, MediaStore.VOLUME_EXTERNAL_PRIMARY, items)

        trackedSafTrees().forEach {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            collectSafItems(resolver, it, items)
        }

        return items.distinct()
    }

    internal suspend fun trackedSafTrees(): List<Uri> {
        val prefs = storagePrefs.data.first()
        val trees = ArrayList<Uri>()

        prefs.storageLocation
            ?.takeIf { it.isNotEmpty() }
            ?.let { trees.add(it.toUri()) }

        prefs.previousSafTrees.mapTo(trees) { it.toUri() }

        return trees.distinct()
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
    private suspend fun legacyTrees(uris: List<Uri>): List<Uri> {
        val currentTreeUri = storageLocation
            .first()
            .takeIf { it != CapturedItemRepository.MEDIA_STORE_LOCATION }
            ?.toUri()

        val trees = ArrayList<Uri>()

        uris.forEach { uri ->
            val authority = uri.authority ?: return@forEach

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

    @Suppress("TooGenericExceptionCaught")
    private fun legacyCapturedItem(uri: Uri): CapturedItem? {
        val columnName = when (uri.authority) {
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
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(CapturedItems.TAG, "unable to read the name of $uri", e)
            }
        }

        return fileName?.let { name ->
            CapturedItems.parseCapturedItem(name, uri)
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
    override suspend fun releaseUntrackedSafTrees() {
    }
}
