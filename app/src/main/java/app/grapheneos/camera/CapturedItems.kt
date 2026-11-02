package app.grapheneos.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.StringRes
import app.grapheneos.camera.CamConfig.SettingValues
import app.grapheneos.camera.util.EphemeralSharedPrefs
import app.grapheneos.camera.util.edit
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.jvm.Throws

typealias ItemType = Int
const val ITEM_TYPE_IMAGE: ItemType = 0
const val ITEM_TYPE_VIDEO: ItemType = 1
const val IMAGE_NAME_PREFIX = "IMG_"
const val VIDEO_NAME_PREFIX = "VID_"

class CapturedItem(
    val type: ItemType,
    val dateString: String,
    val uri: Uri,
    val displayName: String? = null
): Parcelable {
    fun mimeType(): String {
        return if (type == ITEM_TYPE_IMAGE) {
            "image/*"
        } else {
            check(type == ITEM_TYPE_VIDEO)
            "video/*"
        }
    }

    fun uiName(): String {
        val prefix = if (type == ITEM_TYPE_IMAGE) IMAGE_NAME_PREFIX else {
            check(type == ITEM_TYPE_VIDEO)
            VIDEO_NAME_PREFIX
        }

        return displayName ?: "$prefix$dateString"
    }

    /**
     * The capture time this item's own name encodes, in milliseconds. It is the last record of when
     * media was taken once its Exif has been stripped and the provider keeps no creation timestamp,
     * as the Storage Access Framework never does. Null if the name carries no usable timestamp.
     */
    fun captureTime(): Long? {
        // Filename timestamps use the device timezone at capture and cannot recover its offset.
        return parseDateString("yyyyMMdd_HHmmss_SSS") ?: parseDateString("yyyyMMdd_HHmmss")
    }

    private fun parseDateString(pattern: String): Long? {
        val format = SimpleDateFormat(pattern, Locale.US)
        format.isLenient = false
        return try {
            format.parse(dateString)?.time
        } catch (e: ParseException) {
            null
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(type.toByte())
        dest.writeString(dateString)
        uri.writeToParcel(dest, 0)
        dest.writeString(displayName)
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CapturedItem) {
            return false
        }
        return uri == other.uri
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<CapturedItem> {
            @SuppressLint("ParcelClassLoader")
            override fun createFromParcel(source: Parcel): CapturedItem {
                val type = source.readByte().toInt()
                val dateString = source.readString()!!
                val uri = Uri.CREATOR.createFromParcel(source)
                return CapturedItem(type, dateString, uri, source.readString())
            }

            override fun newArray(size: Int) = arrayOfNulls<CapturedItem>(size)
        }
    }

    override fun describeContents() = 0
}

// Some devices throw SecurityException when a media item is no longer accessible.
@StringRes
internal fun shareCapturedItem(activity: Activity, item: CapturedItem): Int? {
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, item.uri)
        setDataAndType(item.uri, item.mimeType())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_image)))
        null
    } catch (e: SecurityException) {
        Log.e(CapturedItems.TAG, "Unable to share media", e)
        R.string.unable_to_share_media
    }
}

// Handle missing media access when launching an external editor.
@StringRes
internal fun editCapturedItem(activity: Activity, item: CapturedItem, useDefaultEditor: Boolean): Int? {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(item.uri, item.mimeType())
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        if (useDefaultEditor) {
            activity.startActivity(intent)
        } else {
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.edit_image)).apply {
                putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
            })
        }
        null
    } catch (e: ActivityNotFoundException) {
        R.string.no_editor_app_error
    } catch (e: SecurityException) {
        Log.e(CapturedItems.TAG, "Unable to edit media", e)
        R.string.unable_to_edit_media
    }
}

object CapturedItems {
    const val TAG = "CapturedItems"

    const val MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES = 5

    fun init(ctx: Context, camConfig: CamConfig) {
        val prefs = camConfig.commonPref

        val legacyPrefKey = "media_uri_s"
        val urisToMigrate = prefs.getString(legacyPrefKey, null)

        if (urisToMigrate != null) {
            prefs.edit {
                migratePreviousUris(ctx, camConfig, urisToMigrate, this, maybeGetCurentSafTree(prefs))
                remove(legacyPrefKey)
            }
        }

        releaseUntrackedSafTrees(ctx, prefs)
    }

    // Release persistent SAF grants for folders no longer tracked by the app.
    fun releaseUntrackedSafTrees(ctx: Context, prefs: SharedPreferences) {
        // Do not revoke durable SAF permissions using a secure session's temporary preferences.
        if (prefs is EphemeralSharedPrefs) {
            return
        }

        val tracked = getSafTrees(prefs)
        val resolver = ctx.contentResolver

        resolver.persistedUriPermissions.forEach { permission ->
            val uri = permission.uri
            val flags = safTreeFlagsToRelease(
                uri, permission.isReadPermission, permission.isWritePermission, tracked
            )
            if (flags == 0) {
                return@forEach
            }

            try {
                resolver.releasePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Unable to release a media permission grant", e)
                }
            }
        }
    }

    // Keep URI permission decisions separate from platform permission objects.
    internal fun safTreeFlagsToRelease(
        uri: Uri, isRead: Boolean, isWrite: Boolean, tracked: Collection<Uri>
    ): Int {
        // Storage locations are the only tree grants this app takes; anything else persisted here
        // belongs to a different feature and is none of our business.
        if (!DocumentsContract.isTreeUri(uri) || tracked.contains(uri)) {
            return 0
        }

        var flags = 0
        if (isRead) {
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        if (isWrite) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

        return flags
    }

    @Throws(InterruptedException::class)
    fun get(ctx: Context): List<CapturedItem> {
        val resolver = ctx.contentResolver
        val list = ArrayList<CapturedItem>()

        collectMediaStoreItems(resolver, MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY), ITEM_TYPE_IMAGE, list)
        collectMediaStoreItems(resolver, MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY), ITEM_TYPE_VIDEO, list)

        getSafTrees(ctx.getSharedPreferences(CamConfig.COMMON_SHARED_PREFS_NAME, Context.MODE_PRIVATE)).forEach {
            if (Thread.interrupted()) {
                // executor is shutting down
                throw InterruptedException()
            }
            collectSafItems(resolver, it, list)
        }

        return list.distinct()
    }

    private fun collectMediaStoreItems(
        resolver: ContentResolver, collection: Uri, type: ItemType, dest: ArrayList<CapturedItem>
    ) {
        val columns = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        try {
            resolver.query(collection, columns, null, null, null)?.use { cursor ->
                dest.ensureCapacity(dest.size + cursor.count)
                val formatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                while (cursor.moveToNext()) {
                    if (Thread.interrupted()) throw InterruptedException()
                    val name = cursor.getString(1) ?: continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    val captured = parseCapturedItem(name, uri)
                    if (captured != null && captured.type == type) {
                        dest.add(captured)
                    } else {
                        val timestamp = (cursor.getLong(2).takeIf { it > 0L }
                            ?: cursor.getLong(3)).coerceAtLeast(0L) * 1000L
                        val date = formatter.format(Date(timestamp))
                        dest.add(CapturedItem(type, date, uri, name))
                    }
                }
            }
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Exception) {
            Log.d(TAG, "Unable to collect media from $collection", error)
        }
    }

    private fun collectSafItems(resolver: ContentResolver, treeUri: Uri, dest: ArrayList<CapturedItem>) {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val childDocumentsUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)

        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val idColumn = 0
        val nameColumn = 1

        try {
            resolver.query(childDocumentsUri, columns, null, null)?.use {
                dest.ensureCapacity(it.count)

                while (it.moveToNext()) {
                    val name = it.getString(nameColumn)
                    val id = it.getString(idColumn)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

                    parseCapturedItem(name, uri)?.let {
                        dest.add(it)
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Unable to collect SAF items", e)
            }
        }
    }

    fun maybeGetCurentSafTree(prefs: SharedPreferences): Uri? {
        return prefs.getString(SettingValues.Key.STORAGE_LOCATION, null)?.let {
            if (it != SettingValues.Default.STORAGE_LOCATION) {
                Uri.parse(it)
            } else {
                null
            }
        }
    }

    fun getSafTrees(prefs: SharedPreferences): List<Uri> {
        val list = ArrayList<Uri>()

        maybeGetCurentSafTree(prefs)?.let {
            list.add(it)
        }

        list.addAll(getPreviousSafTrees(prefs))

        return list.distinct()
    }

    // save few last SAF trees to include their contents in the gallery
    // format: '\0' separated concatenated uri strings, most recent come first

    const val SAF_TREE_SEPARATOR = "\u0000"

    fun getPreviousSafTrees(prefs: SharedPreferences): MutableList<Uri> {
        prefs.getString(SettingValues.Key.PREVIOUS_SAF_TREES, null)?.let {
            return it.split(SAF_TREE_SEPARATOR).map { Uri.parse(it) }.toMutableList()
        }
        return ArrayList()
    }

    fun savePreviousSafTree(treeUri: Uri, prefs: SharedPreferences) {
        val list = getPreviousSafTrees(prefs)

        list.remove(treeUri)
        list.add(0, treeUri)

        while (list.size > MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES) {
            // list.removeLast() requires API level 35 now due to Java adding it
            list.removeAt(list.lastIndex)
        }

        prefs.edit {
            savePreviousSafTrees(list, this)
        }
    }

    fun savePreviousSafTrees(trees: List<Uri>, editor: SharedPreferences.Editor) {
        if (trees.isEmpty()) {
            return
        }
        val str = trees.map { it.toString() }.toTypedArray().joinToString(separator = SAF_TREE_SEPARATOR)
        editor.putString(SettingValues.Key.PREVIOUS_SAF_TREES, str)
    }

    private fun migratePreviousUris(ctx: Context, camConfig: CamConfig, joinedUris: String, editor: SharedPreferences.Editor, currentTreeUri: Uri?) {
        val list = ArrayList<Uri>()

        if (joinedUris.isEmpty()) {
            return
        }

        var checkedLastCapturedItem = false

        joinedUris.split(";").forEach { uriString ->
            val uri = Uri.parse(uriString)

            val authority = uri.authority!!

            if (!checkedLastCapturedItem) {
                val columnName = if (authority == MediaStore.AUTHORITY) {
                    MediaStore.MediaColumns.DISPLAY_NAME
                } else {
                    // SAF
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                }

                var fileName: String? = null

                try {
                    val projection = arrayOf(columnName)
                    ctx.contentResolver.query(uri, projection, null, null)?.use {
                        if (it.moveToFirst()) {
                            fileName = it.getString(0)
                        }
                    }
                } catch (ignored: Exception) {}

                fileName?.let {
                    val item = parseCapturedItem(it, uri)
                    if (item != null) {
                        camConfig.updateLastCapturedItem(item)
                    }
                }

                checkedLastCapturedItem = true
            }

            if (authority == MediaStore.AUTHORITY) {
                return@forEach
            }

            val treeId = DocumentsContract.getTreeDocumentId(uri)
            val treeUri = DocumentsContract.buildTreeDocumentUri(authority, treeId)

            if (treeUri == currentTreeUri || list.contains(treeUri)
                // list is small, not worth it to switch to a Set and lose item order
                || treeUri.toString().contains(SAF_TREE_SEPARATOR))
            {
                return@forEach
            }

            list.add(treeUri)
            if (list.size == MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES) {
                return@forEach
            }
        }

        savePreviousSafTrees(list, editor)
    }

    fun parseCapturedItem(fileName: String, uri: Uri): CapturedItem? {
        val prefix = when {
            fileName.startsWith(IMAGE_NAME_PREFIX) -> IMAGE_NAME_PREFIX
            fileName.startsWith(VIDEO_NAME_PREFIX) -> VIDEO_NAME_PREFIX
            fileName.startsWith("SLOMO_") -> "SLOMO_"
            fileName.startsWith("HYPER_") -> "HYPER_"
            else -> return null
        }
        val type = if (prefix == IMAGE_NAME_PREFIX) ITEM_TYPE_IMAGE else ITEM_TYPE_VIDEO
        val prefixLen = prefix.length
        val end = fileName.indexOf('.', prefixLen)
        if (end < prefixLen + "20220102_030405".length) {
            return null
        }

        for (i in prefixLen until end) {
            val ch = fileName[i]
            if ((ch >= '0' && ch <= '9') || ch == '_') {
                continue
            }
            return null
        }

        val dateString = fileName.substring(prefixLen, end)
        return CapturedItem(type, dateString, uri, fileName.substringBeforeLast('.'))
    }
}
