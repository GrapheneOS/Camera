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
import app.grapheneos.camera.util.edit
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.jvm.Throws

typealias ItemType = Int
const val ITEM_TYPE_IMAGE: ItemType = 0
const val ITEM_TYPE_VIDEO: ItemType = 1
const val IMAGE_NAME_PREFIX = "IMG_"
const val VIDEO_NAME_PREFIX = "VID_"

class CapturedItem(
    val type: ItemType,
    val dateString: String,
    val uri: Uri
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

        return "$prefix$dateString"
    }

    /**
     * The capture time this item's own name encodes, in milliseconds. It is the last record of when
     * media was taken once its Exif has been stripped and the provider keeps no creation timestamp,
     * as the Storage Access Framework never does. Null if the name carries no usable timestamp.
     */
    fun captureTime(): Long? {
        // ImageSaver appends milliseconds to the name and VideoCapturer does not. Both name in
        // whatever the default time zone was at capture, which nothing records, so the name is read
        // back in the current one: the wall-clock digits survive a change of zone, the instant does
        // not. Callers must not present this as a zoned timestamp.
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
    }

    override fun hashCode(): Int {
        return dateString.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CapturedItem) {
            return false
        }
        return dateString == other.dateString
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<CapturedItem> {
            @SuppressLint("ParcelClassLoader")
            override fun createFromParcel(source: Parcel): CapturedItem {
                val type = source.readByte().toInt()
                val dateString = source.readString()!!
                val uri = Uri.CREATOR.createFromParcel(source)
                return CapturedItem(type, dateString, uri)
            }

            override fun newArray(size: Int) = arrayOfNulls<CapturedItem>(size)
        }
    }

    override fun describeContents() = 0
}

// Some OEM builds grant the shared uri to the target inside startActivity(), which throws a
// SecurityException when this app has itself lost access to the item (e.g. it was deleted
// externally, or its persisted uri came from a restored backup)
internal fun shareCapturedItem(activity: Activity, item: CapturedItem): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, item.uri)
        setDataAndType(item.uri, item.mimeType())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_image)))
        true
    } catch (e: SecurityException) {
        Log.e(CapturedItems.TAG, "unable to share ${item.uiName()}", e)
        false
    }
}

// The uri grant for a directly started editor is computed inside startActivity() on all Android
// versions (and inside the chooser start on the OEM builds mentioned above), failing the same way
// when the item is no longer accessible. Returns the error message to show, or null on success
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
        Log.e(CapturedItems.TAG, "unable to edit ${item.uiName()}", e)
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
    }

    @Throws(InterruptedException::class)
    fun get(ctx: Context): List<CapturedItem> {
        val resolver = ctx.contentResolver
        val list = ArrayList<CapturedItem>()

        collectMediaStoreItems(resolver, MediaStore.VOLUME_EXTERNAL_PRIMARY, list)

        getSafTrees(ctx.getSharedPreferences(CamConfig.COMMON_SHARED_PREFS_NAME, Context.MODE_PRIVATE)).forEach {
            if (Thread.interrupted()) {
                // executor is shutting down
                throw InterruptedException()
            }
            collectSafItems(resolver, it, list)
        }

        return list.distinct()
    }

    private fun collectMediaStoreItems(resolver: ContentResolver, volumeName: String, dest: ArrayList<CapturedItem>) {
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

                    parseCapturedItem(name, uri)?.let {
                        dest.add(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "unable to collect MediaStore items, volume $volumeName", e)
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
                Log.d(TAG, "unable to collect SAF items, treeUri $treeUri", e)
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
        val type = if (fileName.startsWith(IMAGE_NAME_PREFIX)) {
            ITEM_TYPE_IMAGE
        } else if (fileName.startsWith(VIDEO_NAME_PREFIX)) {
            ITEM_TYPE_VIDEO
        } else {
            return null
        }

        check(IMAGE_NAME_PREFIX.length == VIDEO_NAME_PREFIX.length)

        val prefixLen = IMAGE_NAME_PREFIX.length
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
        return CapturedItem(type, dateString, uri)
    }
}
