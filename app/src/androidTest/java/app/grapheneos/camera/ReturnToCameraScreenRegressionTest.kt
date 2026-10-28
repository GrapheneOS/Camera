package app.grapheneos.camera

import android.Manifest
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.capturer.ImageSaver
import app.grapheneos.camera.ui.activities.MainActivity
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Returning to the camera screen, such as from the gallery, cleared the thumbnail and reloaded it,
 * so it flashed empty each time, for as many frames as the reload took.
 */
@RunWith(AndroidJUnit4::class)
class ReturnToCameraScreenRegressionTest {

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
    fun thumbnail_onReturnToTheScreen_staysShownWhileItReloads() {
        val last = captures.insertImage("20991231_235959")
        captures.save(last)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail is loaded") { it.isThumbnailLoaded }
            var loaded: Bitmap? = null
            scenario.onActivity { loaded = shownThumbnail(it) }

            returnToTheScreenWhileTheThumbnailReloads(scenario) {
                assertSame("the thumbnail went empty", loaded, shownThumbnail(it))
            }

            waitUntil(scenario, "the thumbnail is reloaded") { it.isThumbnailLoaded }
        }
    }

    @Test
    fun thumbnail_ofAPhotoJustTaken_staysShownOnReturnToTheScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }
            var capturedBefore: Uri? = null
            scenario.onActivity { capturedBefore = it.camConfig.lastCapturedItem?.uri }

            lateinit var saver: ImageSaver
            scenario.onActivity { saver = imageSaverFor(it) }
            try {
                ImageSaver.imageCaptureCallbackExecutor.execute {
                    saver.onCaptureSuccess(JpegImageProxy(noiseJpeg()))
                }
                waitUntil(scenario, "the photo has its thumbnail") {
                    it.camConfig.lastCapturedItem?.uri != capturedBefore
                            && it.previewLoader.visibility == View.GONE
                }
                var generated: Bitmap? = null
                scenario.onActivity { generated = shownThumbnail(it) }
                assertNotNull("no thumbnail was made of the photo", generated)

                returnToTheScreenWhileTheThumbnailReloads(scenario) {
                    assertSame("the thumbnail went empty", generated, shownThumbnail(it))
                }
            } finally {
                scenario.onActivity {
                    val item = it.camConfig.lastCapturedItem ?: return@onActivity
                    if (item.uri != capturedBefore) {
                        it.contentResolver.delete(item.uri, null, null)
                    }
                }
            }
        }
    }

    @Test
    fun thumbnail_ofTheCaptureBefore_isNotShownForANewerOne() {
        val older = captures.insertImage("20991231_235958")
        val newer = captures.insertImage("20991231_235959")
        captures.save(older)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "the thumbnail is loaded") { it.isThumbnailLoaded }

            // As taken in another camera screen, such as the one opened from the lock screen
            returnToTheScreenWhileTheThumbnailReloads(scenario, whileAway = { captures.save(newer) }) {
                assertNull("the thumbnail of another capture is shown", shownThumbnail(it))
            }

            waitUntil(scenario, "the thumbnail shows the newer capture") {
                it.isThumbnailLoaded && it.camConfig.lastCapturedItem?.uri == newer.uri
            }
        }
    }

    /** Checks the thumbnail once back, before the reload that returning starts can finish. */
    private fun returnToTheScreenWhileTheThumbnailReloads(
        scenario: ActivityScenario<MainActivity>,
        whileAway: () -> Unit = {},
        check: (MainActivity) -> Unit,
    ) {
        scenario.moveToState(Lifecycle.State.CREATED)
        whileAway()

        val reload = CountDownLatch(1)
        scenario.onActivity { it.thumbnailLoaderExecutor.execute { reload.await() } }
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertTrue(it.isStarted)
                check(it)
            }
        } finally {
            reload.countDown()
        }
    }

    private fun shownThumbnail(activity: MainActivity) =
        (activity.imagePreview.drawable as? BitmapDrawable)?.bitmap
}
