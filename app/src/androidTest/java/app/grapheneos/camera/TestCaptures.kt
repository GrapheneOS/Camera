package app.grapheneos.camera

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.CamConfig.SettingValues
import org.junit.rules.ExternalResource

class TestCaptures : ExternalResource() {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    val prefs: SharedPreferences =
        context.getSharedPreferences(CamConfig.COMMON_SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    private lateinit var savedPrefs: Map<String, Any?>
    private val inserted = ArrayList<Uri>()

    override fun before() {
        savedPrefs = RESTORED_KEYS.associateWith { prefs.all[it] }
    }

    override fun after() {
        inserted.forEach { resolver.delete(it, null, null) }
        prefs.edit().apply {
            savedPrefs.forEach { (key, value) ->
                when (value) {
                    null -> remove(key)
                    is Int -> putInt(key, value)
                    else -> putString(key, value as String)
                }
            }
        }.commit()
    }

    val savedUri: String?
        get() = prefs.getString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, null)

    fun save(item: CapturedItem) {
        prefs.edit()
            .putInt(SettingValues.Key.LAST_CAPTURED_ITEM_TYPE, item.type)
            .putString(SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING, item.dateString)
            .putString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, item.uri.toString())
            .commit()
    }

    // Not savePreviousSafTree, which can push a real folder off the list and get its grant released
    fun track(tree: Uri) {
        val trees = CapturedItems.getPreviousSafTrees(prefs) + tree
        prefs.edit()
            .putString(
                SettingValues.Key.PREVIOUS_SAF_TREES,
                trees.joinToString(CapturedItems.SAF_TREE_SEPARATOR),
            )
            .commit()
    }

    fun delete(item: CapturedItem) {
        resolver.delete(item.uri, null, null)
        inserted -= item.uri
    }

    // Pass a date far ahead, so that none of the device's own captures is newer
    fun insertImage(dateString: String): CapturedItem {
        val uri = insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "$IMAGE_NAME_PREFIX$dateString.jpg",
            "image/jpeg",
        )
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        resolver.openOutputStream(uri)!!.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        return CapturedItem(ITEM_TYPE_IMAGE, dateString, uri)
    }

    fun insertVideo(dateString: String): CapturedItem {
        val uri = insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            "$VIDEO_NAME_PREFIX$dateString.mp4",
            "video/mp4",
        )
        return CapturedItem(ITEM_TYPE_VIDEO, dateString, uri)
    }

    private fun insert(collection: Uri, name: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }
        return resolver.insert(collection, values)!!.also { inserted += it }
    }

    private companion object {
        val RESTORED_KEYS = listOf(
            SettingValues.Key.LAST_CAPTURED_ITEM_TYPE,
            SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING,
            SettingValues.Key.LAST_CAPTURED_ITEM_URI,
            SettingValues.Key.PREVIOUS_SAF_TREES,
        )
    }
}
