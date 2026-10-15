package app.grapheneos.camera

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A snapshot taken while recording disabled the shutter until it was saved, and a volume or camera
 * key, which only presses an enabled shutter, could not stop the recording in the meantime.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotWhileRecordingRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun volumeKey_whileASnapshotIsTaken_stopsTheRecording() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)
            scenario.onActivity { it.camConfig.switchMode(CameraMode.VIDEO) }
            waitUntil(scenario, "video use case is bound") { it.camConfig.videoCapture != null }
            var canSnapshot = false
            scenario.onActivity { canSnapshot = it.camConfig.canTakePicture }
            assumeTrue("this camera takes no snapshot while recording", canSnapshot)
            scenario.onActivity { it.camConfig.mPlayer = ImmediateTunePlayer(it) }
            // Seconds, and one early, as MediaStore keeps the time a file was added
            val startedS = System.currentTimeMillis() / 1000 - 1
            try {
                scenario.onActivity { it.captureButton.performClick() }
                awaitRecordingStarted(scenario)

                scenario.onActivity { activity ->
                    activity.thirdCircle.performClick()
                    // Still in flight: what it reports back is posted to this thread
                    assertTrue(activity.imageCapturer.isTakingPicture)

                    pressVolumeDown(activity)
                }

                waitUntil(scenario, "the recording stops") { !it.videoCapturer.isRecording }
            } finally {
                runCatching { scenario.onActivity { it.videoCapturer.stopRecording() } }
                runCatching { awaitOutputSettled(scenario) }
                // The video and, once it is saved, the snapshot
                runCatching {
                    waitUntil(scenario, "the snapshot is saved") { capturesSince(startedS).size >= 2 }
                }
                capturesSince(startedS).forEach { context.contentResolver.delete(it, null, null) }
            }
        }
    }

    /** What this run saved: the camera's own captures, added since [sinceS]. */
    private fun capturesSince(sinceS: Long): List<Uri> {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaColumns.DATE_ADDED} >= ?"
        val args = arrayOf(context.packageName, sinceS.toString())
        return context.contentResolver.query(files, arrayOf(MediaColumns._ID), selection, args, null)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(ContentUris.withAppendedId(files, cursor.getLong(0)))
                    }
                }
            } ?: emptyList()
    }

    private fun pressVolumeDown(activity: MainActivity) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            activity.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_VOLUME_DOWN))
        }
    }
}
