package app.grapheneos.camera

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.CamConfig.SettingValues
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
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

    @get:Rule
    val captures = TestCaptures()

    @Test
    fun thumbnail_afterTheNewestCaptureIsDeleted_showsTheOneBefore() {
        val older = captures.insertImage("20991231_235958")
        val newest = captures.insertImage("20991231_235959")
        captures.save(newest)
        captures.delete(newest)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail shows the capture before") {
                it.isThumbnailLoaded && it.camConfig.lastCapturedItem == older
            }
        }
    }

    @Test
    fun thumbnail_afterTheNewestCaptureIsDeleted_showsItsNamesakeInAnotherFolder() {
        captures.insertImage("20991231_235958")
        val newest = captures.insertImage("20991231_235959")
        val namesake = captures.insertImage("20991231_235959", folder = "DCIM/")
        captures.save(newest)
        captures.delete(newest)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Items equal by name, so only the row tells the namesake from the deleted one
            waitUntil(scenario, "the thumbnail shows the namesake") {
                val shown = it.camConfig.lastCapturedItem
                it.isThumbnailLoaded && shown != null && rowOf(shown.uri) == rowOf(namesake.uri)
            }
        }

        assertEquals(rowOf(namesake.uri), rowOf(Uri.parse(captures.savedUri)))
    }

    @Test
    fun savedLastCapture_afterTheNewestCaptureIsDeleted_pointsAtTheOneBefore() {
        val older = captures.insertImage("20991231_235958")
        val newest = captures.insertImage("20991231_235959")
        captures.save(newest)
        captures.delete(newest)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail is loaded") { it.isThumbnailLoaded }
        }

        assertEquals(
            older.dateString,
            captures.prefs.getString(SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING, null),
        )
        // The media scan names it by its Files uri, which shares the row id
        val saved = Uri.parse(captures.savedUri)
        assertEquals(ContentUris.parseId(older.uri), ContentUris.parseId(saved))
    }

    @Test
    fun secureSessionThumbnail_afterItsNewestCaptureIsDeleted_showsOnlyItsOwnCapture() {
        val sessionOlder = captures.insertImage("20991231_235957")
        captures.insertImage("20991231_235958")
        val sessionNewest = captures.insertImage("20991231_235959")
        captures.save(sessionNewest)
        captures.delete(sessionNewest)

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
        assertEquals(sessionNewest.uri.toString(), captures.savedUri)
    }

    @Test
    fun secureSessionThumbnail_afterItsNewestCaptureIsDeleted_skipsANamesakeItDidNotTake() {
        val sessionOlder = captures.insertImage("20991231_235957")
        val sessionNewest = captures.insertImage("20991231_235959")
        captures.insertImage("20991231_235959", folder = "DCIM/")
        captures.save(sessionNewest)
        captures.delete(sessionNewest)

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
    }

    @Test
    fun videoOnlyThumbnail_afterTheNewestVideoIsDeleted_showsNoImage() {
        captures.insertImage("20991231_235959")
        val video = captures.insertVideo("20991231_235958")
        captures.save(video)
        captures.delete(video)

        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "the deleted video is replaced") {
                it.camConfig.lastCapturedItem != video
            }
            scenario.onActivity {
                assertNotEquals(ITEM_TYPE_IMAGE, it.camConfig.lastCapturedItem?.type)
            }
        }

        // Left for the regular camera, which also sees the newer image, to replace
        assertEquals(video.uri.toString(), captures.savedUri)
    }

    // The media scan names a file by its Files uri, which shares the row id
    private fun rowOf(uri: Uri) = ContentUris.parseId(uri)
}
