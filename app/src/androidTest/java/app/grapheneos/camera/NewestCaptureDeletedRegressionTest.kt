package app.grapheneos.camera

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.CamConfig.SettingValues
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting the newest capture left the thumbnail blank until the next capture, even across
 * restarts, as the item saved for it still pointed at the deleted one.
 */
@RunWith(AndroidJUnit4::class)
class NewestCaptureDeletedRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val prefs =
        context.getSharedPreferences(CamConfig.COMMON_SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    private val savedPrefs = LAST_CAPTURED_ITEM_KEYS.associateWith { prefs.all[it] }
    private val inserted = ArrayList<Uri>()

    @After
    fun cleanUp() {
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

    @Test
    fun thumbnail_afterTheNewestCaptureIsDeleted_showsTheOneBefore() {
        val older = insertImage("20991231_235958")
        val newest = insertImage("20991231_235959")
        save(newest)
        delete(newest)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail shows the capture before") {
                it.isThumbnailLoaded && it.camConfig.lastCapturedItem == older
            }
        }
    }

    @Test
    fun savedLastCapture_afterTheNewestCaptureIsDeleted_pointsAtTheOneBefore() {
        val older = insertImage("20991231_235958")
        val newest = insertImage("20991231_235959")
        save(newest)
        delete(newest)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail is loaded") { it.isThumbnailLoaded }
        }

        assertEquals(
            older.dateString,
            prefs.getString(SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING, null),
        )
        // The media scan names it by its Files uri, which shares the row id
        val saved = Uri.parse(prefs.getString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, null))
        assertEquals(ContentUris.parseId(older.uri), ContentUris.parseId(saved))
    }

    @Test
    fun secureSessionThumbnail_afterItsNewestCaptureIsDeleted_showsOnlyItsOwnCapture() {
        val sessionOlder = insertImage("20991231_235957")
        insertImage("20991231_235958")
        val sessionNewest = insertImage("20991231_235959")
        save(sessionNewest)
        delete(sessionNewest)

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.capturedItems.addAll(listOf(sessionOlder, sessionNewest))
                it.camConfig.lastCapturedItem = sessionNewest
                it.updateThumbnail()
            }

            waitUntil(scenario, "the thumbnail shows the session's capture before") {
                it.isThumbnailLoaded && it.camConfig.lastCapturedItem == sessionOlder
            }
        }

        // Left for the regular camera, which sees every item, to replace
        assertEquals(
            sessionNewest.uri.toString(),
            prefs.getString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, null),
        )
    }

    @Test
    fun videoOnlyThumbnail_afterTheNewestVideoIsDeleted_showsNoImage() {
        insertImage("20991231_235959")
        val video = insertVideo("20991231_235958")
        save(video)
        delete(video)

        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "the deleted video is replaced") {
                it.camConfig.lastCapturedItem != video
            }
            scenario.onActivity {
                assertNotEquals(ITEM_TYPE_IMAGE, it.camConfig.lastCapturedItem?.type)
            }
        }

        // Left for the regular camera, which also sees the newer image, to replace
        assertEquals(
            video.uri.toString(),
            prefs.getString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, null),
        )
    }

    private fun delete(item: CapturedItem) {
        resolver.delete(item.uri, null, null)
        inserted -= item.uri
    }

    private fun save(item: CapturedItem) {
        prefs.edit()
            .putInt(SettingValues.Key.LAST_CAPTURED_ITEM_TYPE, item.type)
            .putString(SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING, item.dateString)
            .putString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, item.uri.toString())
            .commit()
    }

    /** Dated far ahead, so that none of the device's own captures is newer. */
    private fun insertImage(dateString: String): CapturedItem {
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

    /** Left empty, as it is only ever saved and then deleted. */
    private fun insertVideo(dateString: String): CapturedItem {
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
        val LAST_CAPTURED_ITEM_KEYS = listOf(
            SettingValues.Key.LAST_CAPTURED_ITEM_TYPE,
            SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING,
            SettingValues.Key.LAST_CAPTURED_ITEM_URI,
        )
    }
}
