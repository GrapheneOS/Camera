package app.grapheneos.camera

import android.Manifest
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A capture session's failed photo left its loader spinning over a session that could not be
 * finished from there, and a second shutter press made one fail as soon as the first one's photo
 * unbound the camera.
 */
@RunWith(AndroidJUnit4::class)
class FailedIntentCaptureRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    @Test
    fun secondShutterPress_whileThePhotoIsTaken_isIgnored() {
        launchCaptureSession(CaptureActivity::class.java, MediaStore.ACTION_IMAGE_CAPTURE)
            .use { scenario ->
                awaitShutterReady(scenario)

                scenario.onActivity {
                    it.captureButton.performClick()
                    it.captureButton.performClick()
                }
                awaitConfirmScreen(scenario)

                // A second capture fails some time after the first one unbinds the camera, and
                // the absence of that failure cannot be waited for
                Thread.sleep(FAILURE_DWELL_MS)
                scenario.onActivity {
                    assertEquals(
                        it.getString(R.string.image_captured_successfully),
                        snackbarText(it),
                    )
                }
            }
    }

    @Test
    fun capture_onAnUnboundCamera_hidesTheLoaderAndCanBeRetried() {
        launchCaptureSession(CaptureActivity::class.java, MediaStore.ACTION_IMAGE_CAPTURE)
            .use { scenario ->
                awaitShutterReady(scenario)

                scenario.onActivity {
                    it.camConfig.cameraProvider!!.unbindAll()
                    it.captureButton.performClick()
                }
                waitUntil(scenario, "the capture fails") {
                    snackbarText(it) == it.getString(R.string.unable_to_capture_image)
                }
                scenario.onActivity {
                    assertEquals(View.GONE, it.previewLoader.visibility)
                    assertFalse(it.isFinishing)
                }

                scenario.onActivity { it.camConfig.startCamera(true) }
                awaitShutterReady(scenario)
                scenario.onActivity { it.captureButton.performClick() }
                awaitConfirmScreen(scenario)
            }
    }

    private fun snackbarText(activity: MainActivity) =
        activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.text?.toString()

    private companion object {
        const val FAILURE_DWELL_MS = 1000L
    }
}
