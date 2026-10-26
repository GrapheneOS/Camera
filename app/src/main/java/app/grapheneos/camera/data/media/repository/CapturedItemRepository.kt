package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.util.Size
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
import app.grapheneos.camera.data.media.store.imageCollectionUri
import app.grapheneos.camera.data.media.store.videoCollectionUri
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.di.core.VideoFrameDispatcher
import app.grapheneos.camera.util.ImageDownscaler
import app.grapheneos.camera.util.OrientedImageDownscaler
import app.grapheneos.camera.util.getStringOrNull
import app.grapheneos.camera.util.queryFirstRowOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
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

    /**
     * The system's cached thumbnail of [item], or else [item] decoded to fit the given size; null
     * when it can't be read.
     */
    suspend fun loadThumbnail(
        item: CapturedItem,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap?

    /** [item] decoded to fit the screen. */
    suspend fun loadPhotoPreview(item: CapturedItem): Bitmap?

    /** [item] decoded at full resolution, scaled down only where it is too large to draw. */
    suspend fun loadFullPhoto(item: CapturedItem): Bitmap?

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
    @VideoFrameDispatcher private val videoFrameDispatcher: CoroutineDispatcher,
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
        return withContext(ioDispatcher) {
            val items = ArrayList<CapturedItem>()

            collectMediaStoreItems(
                collection = imageCollectionUri,
                dest = items,
            )
            collectMediaStoreItems(
                collection = videoCollectionUri,
                dest = items,
            )

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

            contentResolver.registerContentObserver(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                true,
                observer,
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                true,
                observer,
            )

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

    override suspend fun loadThumbnail(
        item: CapturedItem,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val cached = loadCachedThumbnail(
            item = item,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        return cached ?: when (item.type) {
            CapturedItemType.VIDEO -> loadVideoFrame(
                item = item,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )

            CapturedItemType.IMAGE -> loadPhoto(
                item = item,
                downscaler = ImageDownscaler(
                    maxWidth = targetWidth,
                    maxHeight = targetHeight,
                ),
            )
        }
    }

    override suspend fun loadPhotoPreview(item: CapturedItem): Bitmap? {
        return loadPhoto(
            item = item,
            downscaler = screenDownscaler(),
        )
    }

    override suspend fun loadFullPhoto(item: CapturedItem): Bitmap? {
        return loadPhoto(
            item = item,
            downscaler = ImageDownscaler(
                maxWidth = FULL_PHOTO_MAX_SIDE,
                maxHeight = FULL_PHOTO_MAX_SIDE,
            ),
        )
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

    @Suppress("TooGenericExceptionCaught")
    private suspend fun collectMediaStoreItems(
        collection: Uri,
        dest: ArrayList<CapturedItem>,
    ) {
        try {
            contentResolver.query(
                collection,
                MEDIA_STORE_COLUMNS,
                null,
                null,
            )?.use {
                dest.ensureCapacity(dest.size + it.count)

                while (currentCoroutineContext().isActive && it.moveToNext()) {
                    val name = it.getString(NAME_COLUMN)
                    val uri = ContentUris.withAppendedId(collection, it.getLong(ID_COLUMN))

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
    private suspend fun collectSafItems(
        treeUri: Uri,
        dest: ArrayList<CapturedItem>,
    ) {
        val childDocumentsUri = childDocumentsUri(treeUri)

        try {
            contentResolver.query(
                childDocumentsUri,
                DOCUMENT_COLUMNS,
                null,
                null,
            )?.use {
                dest.ensureCapacity(dest.size + it.count)

                while (currentCoroutineContext().isActive && it.moveToNext()) {
                    val name = it.getString(NAME_COLUMN)
                    val id = it.getString(ID_COLUMN)
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

    private fun childDocumentsUri(tree: Uri): Uri {
        val treeId = DocumentsContract.getTreeDocumentId(tree)

        return DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
    }

    private suspend fun loadCachedThumbnail(
        item: CapturedItem,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        return withContext(ioDispatcher) {
            try {
                contentResolver.loadThumbnail(
                    item.uri,
                    Size(targetWidth, targetHeight),
                    null,
                )
            } catch (e: IOException) {
                Log.d(TAG, "no cached thumbnail for ${item.uri}", e)
                null
            }
        }
    }

    private suspend fun loadVideoFrame(
        item: CapturedItem,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        return withContext(videoFrameDispatcher) {
            decodeOrNull(item) {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, item.uri)
                    retriever.getScaledFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetWidth,
                        targetHeight,
                    )
                }
            }
        }
    }

    private suspend fun loadPhoto(
        item: CapturedItem,
        downscaler: ImageDecoder.OnHeaderDecodedListener,
    ): Bitmap? {
        return withContext(ioDispatcher) {
            decodeOrNull(item) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(contentResolver, item.uri),
                    downscaler,
                )
            }
        }
    }

    // The screen can be rotated, so the photo's longer side is fitted to the screen's longer side.
    private fun screenDownscaler(): OrientedImageDownscaler {
        val metrics = context.resources.displayMetrics

        return OrientedImageDownscaler(
            maxLongSide = max(metrics.widthPixels, metrics.heightPixels),
            maxShortSide = min(metrics.widthPixels, metrics.heightPixels),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun decodeOrNull(
        item: CapturedItem,
        decode: () -> Bitmap?,
    ): Bitmap? {
        return try {
            decode()
        } catch (e: Exception) {
            Log.d(TAG, "unable to decode ${item.uri}", e)
            null
        }
    }

    companion object {
        // Recent storage locations stay tracked so the gallery can still show their contents.
        const val MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES = 5

        private const val TAG = "CapturedItemRepository"

        // A canvas refuses to draw a bitmap over 100 MB; 4500 × 4500 px at 4 bytes per pixel is 81 MB
        private const val FULL_PHOTO_MAX_SIDE = 4500
        private const val LEGACY_MEDIA_URI_SEPARATOR = ";"

        private const val ID_COLUMN = 0
        private const val NAME_COLUMN = 1

        private val MEDIA_STORE_COLUMNS = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
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
