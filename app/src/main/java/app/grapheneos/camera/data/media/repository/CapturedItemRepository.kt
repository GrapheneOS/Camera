package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import app.grapheneos.camera.BuildConfig
import app.grapheneos.camera.data.media.mapper.CapturedItemNameMapper
import app.grapheneos.camera.data.media.mapper.SafTreeReleaseFlagsMapper
import app.grapheneos.camera.data.media.mapper.StoredCapturedItemMapper
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.store.MediaPrefs
import app.grapheneos.camera.data.media.store.StoragePrefs
import app.grapheneos.camera.data.media.store.StoragePrefsMigration
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.util.ImageDownscaler
import app.grapheneos.camera.util.getStringOrNull
import app.grapheneos.camera.util.getVideoThumbnail
import app.grapheneos.camera.util.queryFirstRowOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface CapturedItemRepository {

    val lastCapturedItem: Flow<CapturedItem?>

    suspend fun saveLastCapturedItem(item: CapturedItem)

    val storageLocation: Flow<String>

    suspend fun setStorageLocation(value: String)

    fun canStoreSafTree(tree: Uri): Boolean

    suspend fun persistSafTreeAccess(tree: Uri)

    suspend fun releaseUntrackedSafTrees()

    suspend fun migrateStoredCaptures(): CapturedItem?

    suspend fun capturedItems(): List<CapturedItem>

    /** Emits whenever the items [capturedItems] lists may have changed. */
    fun capturedItemsChanges(): Flow<Unit>

    /** Decodes [item] to fit within the given size, or returns null when it can't be read. */
    suspend fun loadThumbnail(
        item: CapturedItem,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap?

    suspend fun loadPreview(item: CapturedItem): Bitmap?

    companion object {
        const val MEDIA_STORE_LOCATION = ""
    }
}

internal class CapturedItemRepositoryImpl @Inject constructor(
    private val storagePrefs: DataStore<StoragePrefs>,
    private val mediaPrefs: DataStore<MediaPrefs>,
    private val capturedItemNameMapper: CapturedItemNameMapper,
    private val storedCapturedItemMapper: StoredCapturedItemMapper,
    private val safTreeReleaseFlagsMapper: SafTreeReleaseFlagsMapper,
    private val contentResolver: ContentResolver,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CapturedItemRepository {

    override val lastCapturedItem: Flow<CapturedItem?> = mediaPrefs.data.map { prefs ->
        prefs.lastCapturedItem?.let { stored ->
            storedCapturedItemMapper.map(stored)
        }
    }

    override suspend fun saveLastCapturedItem(item: CapturedItem) {
        mediaPrefs.updateData {
            it.copy(lastCapturedItem = storedCapturedItemMapper.map(item))
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

                    while (trees.size > MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES) {
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

    override fun canStoreSafTree(tree: Uri): Boolean {
        return !tree.toString().contains(StoragePrefsMigration.SAF_TREE_SEPARATOR)
    }

    override suspend fun persistSafTreeAccess(tree: Uri) {
        withContext(ioDispatcher) {
            contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun releaseUntrackedSafTrees() {
        withContext(ioDispatcher) {
            val tracked = trackedSafTrees()

            contentResolver.persistedUriPermissions.forEach { permission ->
                val uri = permission.uri
                val flags = safTreeReleaseFlagsMapper.map(
                    uri = uri,
                    isRead = permission.isReadPermission,
                    isWrite = permission.isWritePermission,
                    tracked = tracked,
                )

                if (flags == 0) {
                    return@forEach
                }

                try {
                    contentResolver.releasePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "unable to release the grant for $uri", e)
                    }
                }
            }
        }
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

    private fun legacyCapturedItem(uri: Uri): CapturedItem? {
        val columnName = when (uri.authority) {
            MediaStore.AUTHORITY -> MediaStore.MediaColumns.DISPLAY_NAME
            else -> DocumentsContract.Document.COLUMN_DISPLAY_NAME
        }

        val fileName = contentResolver.queryFirstRowOrNull(
            uri = uri,
            projection = arrayOf(columnName)
        ) { cursor ->
            cursor.getStringOrNull(columnName)
        }

        return fileName?.let { name ->
            capturedItemNameMapper.map(name, uri)
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
                treeUri.toString().contains(StoragePrefsMigration.SAF_TREE_SEPARATOR)

            if (skip) {
                return@forEach
            }

            trees.add(treeUri)
        }

        return trees
    }

    override suspend fun capturedItems(): List<CapturedItem> {
        return withContext(ioDispatcher) {
            val items = ArrayList<CapturedItem>()

            collectMediaStoreItems(items)

            trackedSafTrees().forEach {
                ensureActive()
                collectSafItems(it, items)
            }

            items.distinct()
        }
    }

    override fun capturedItemsChanges(): Flow<Unit> {
        return callbackFlow {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }

            contentResolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)

            trackedSafTrees().forEach { tree ->
                observeSafTree(
                    tree = tree,
                    observer = observer,
                )
            }

            awaitClose {
                contentResolver.unregisterContentObserver(observer)
            }
        }.flowOn(ioDispatcher)
    }

    private fun observeSafTree(
        tree: Uri,
        observer: ContentObserver,
    ) {
        try {
            contentResolver.registerContentObserver(childDocumentsUri(tree), true, observer)
        } catch (e: SecurityException) {
            Log.d(TAG, "unable to observe $tree", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun collectMediaStoreItems(dest: ArrayList<CapturedItem>) {
        val volumeUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val columns = arrayOf(BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val idColumn = 0
        val nameColumn = 1

        try {
            contentResolver.query(volumeUri, columns, null, null)?.use {
                dest.ensureCapacity(it.count)

                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)
                    val uri = ContentUris.withAppendedId(volumeUri, it.getLong(idColumn))

                    capturedItemNameMapper.map(name, uri)?.let { item ->
                        dest.add(item)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "unable to collect MediaStore items", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun collectSafItems(
        treeUri: Uri,
        dest: ArrayList<CapturedItem>,
    ) {
        val childDocumentsUri = childDocumentsUri(treeUri)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val idColumn = 0
        val nameColumn = 1

        try {
            contentResolver.query(childDocumentsUri, columns, null, null)?.use {
                dest.ensureCapacity(it.count)

                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)
                    val id = it.getString(idColumn)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

                    capturedItemNameMapper.map(name, uri)?.let { item ->
                        dest.add(item)
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "unable to collect SAF items, treeUri $treeUri", e)
            }
        }
    }

    private fun childDocumentsUri(tree: Uri): Uri {
        val treeId = DocumentsContract.getTreeDocumentId(tree)

        return DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun loadThumbnail(
        item: CapturedItem,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        return withContext(ioDispatcher) {
            try {
                when (item.type) {
                    CapturedItemType.VIDEO -> getVideoThumbnail(context, item.uri)?.let { frame ->
                        scaleDownToFit(
                            frame = frame,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                        )
                    }

                    CapturedItemType.IMAGE -> ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(contentResolver, item.uri),
                        ImageDownscaler(
                            maxWidth = targetWidth,
                            maxHeight = targetHeight,
                        ),
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "unable to decode ${item.uri}", e)
                null
            }
        }
    }

    private fun scaleDownToFit(
        frame: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val width = frame.width.toDouble()
        val height = frame.height.toDouble()
        val ratio = max(width / targetWidth, height / targetHeight)

        if (ratio <= 1) {
            return frame
        }

        val scaled = frame.scale(
            width = (width / ratio).toInt(),
            height = (height / ratio).toInt(),
        )
        frame.recycle()

        return scaled
    }

    override suspend fun loadPreview(item: CapturedItem): Bitmap? {
        return loadThumbnail(
            item = item,
            targetWidth = PREVIEW_MAX_SIDE,
            targetHeight = PREVIEW_MAX_SIDE,
        )
    }

    companion object {
        // Recent storage locations stay tracked so the gallery can still show their contents.
        const val MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES = 5

        private const val TAG = "CapturedItemRepository"

        // Keeps a decoded photo clear of the bitmap size limit (100 MB)
        private const val PREVIEW_MAX_SIDE = 4500
        private const val LEGACY_MEDIA_URI_SEPARATOR = ";"
    }
}

internal class LockscreenCapturedItemRepository(
    private val delegate: CapturedItemRepository,
) : CapturedItemRepository by delegate {

    override fun canStoreSafTree(tree: Uri): Boolean {
        return false
    }

    @Suppress("EmptyFunctionBlock")
    override suspend fun releaseUntrackedSafTrees() {
    }
}
