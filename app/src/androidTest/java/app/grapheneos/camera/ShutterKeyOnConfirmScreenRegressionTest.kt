package app.grapheneos.camera

import android.Manifest
import android.provider.MediaStore
import android.view.KeyEvent
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shutter is hidden on a capture session's confirm screen, but a volume or camera key still
 * pressed it, firing a capture into the camera the confirm screen had unbound.
 */
@RunWith(AndroidJUnit4::class)
class ShutterKeyOnConfirmScreenRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    @Test
    fun volumeKey_onThePhotoConfirmScreen_takesNoPicture() {
        launchCaptureSession(CaptureActivity::class.java, MediaStore.ACTION_IMAGE_CAPTURE)
            .use { scenario ->
                awaitShutterReady(scenario)
                scenario.onActivity { it.captureButton.performClick() }
                awaitConfirmScreen(scenario)

                scenario.onActivity { activity ->
                    pressVolumeDown(activity)

                    // A capture replaces this message as soon as it is asked for
                    assertEquals(
                        activity.getString(R.string.image_captured_successfully),
                        snackbarText(activity),
                    )
                }
            }
    }

    @Test
    fun volumeKey_onTheVideoConfirmScreen_startsNoRecording() {
        launchCaptureSession(VideoCaptureActivity::class.java, MediaStore.ACTION_VIDEO_CAPTURE)
            .use { scenario ->
                awaitShutterReady(scenario)
                scenario.onActivity { it.camConfig.mPlayer = ImmediateTunePlayer(it) }
                val capturedBefore = lastCapturedUri(scenario)
                try {
                    scenario.onActivity { it.captureButton.performClick() }
                    recordASecond(scenario)
                    scenario.onActivity { it.captureButton.performClick() }
                    awaitConfirmScreen(scenario)
                    awaitOutputSettled(scenario)

                    scenario.onActivity { activity ->
                        pressVolumeDown(activity)

                        assertFalse(activity.videoCapturer.isRecording)
                    }
                } finally {
                    stopAndDeleteRecording(scenario, capturedBefore)
                }
            }
    }

    private fun pressVolumeDown(activity: MainActivity) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            activity.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_VOLUME_DOWN))
        }
    }

    private fun snackbarText(activity: MainActivity) =
        activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.text?.toString()
}
