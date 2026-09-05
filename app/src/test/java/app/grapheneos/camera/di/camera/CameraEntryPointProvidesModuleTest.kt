package app.grapheneos.camera.di.camera

import android.app.Activity
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.QrTile
import app.grapheneos.camera.ui.activities.SecureCaptureActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraEntryPointProvidesModuleTest {

    private val module = CameraEntryPointProvidesModule()

    private fun <T : Activity> entryPointFor(type: Class<T>): CameraEntryPoint {
        return module.provideCameraEntryPoint(Robolectric.buildActivity(type).get())
    }

    @Test
    fun theMainEntryPoint_offersEverything() {
        assertEquals(
            CameraEntryPoint(
                isSecureSession = false,
                isCaptureSession = false,
                isVideoOnlySession = false,
                requiresVideoModeOnly = false,
                allowsQrScanning = true,
                showsCameraModeTabs = true,
            ),
            entryPointFor(MainActivity::class.java),
        )
    }

    @Test
    fun aLockscreenSession_isSecureAndScansNoCodes() {
        val entryPoint = entryPointFor(SecureMainActivity::class.java)

        assertTrue(entryPoint.isSecureSession)
        assertFalse(entryPoint.allowsQrScanning)
        assertTrue(entryPoint.showsCameraModeTabs)
    }

    @Test
    fun theQrTile_showsNoModeTabs() {
        assertFalse(entryPointFor(QrTile::class.java).showsCameraModeTabs)
    }

    @Test
    fun aCaptureSession_showsNoModeTabsAndStillTakesPhotos() {
        val entryPoint = entryPointFor(CaptureActivity::class.java)

        assertTrue(entryPoint.isCaptureSession)
        assertFalse(entryPoint.showsCameraModeTabs)
        assertFalse(entryPoint.requiresVideoModeOnly)
    }

    @Test
    fun aSecureCaptureSession_isBothSecureAndACapture() {
        val entryPoint = entryPointFor(SecureCaptureActivity::class.java)

        assertTrue(entryPoint.isSecureSession)
        assertTrue(entryPoint.isCaptureSession)
    }

    @Test
    fun aVideoCaptureSession_recordsOnly() {
        val entryPoint = entryPointFor(VideoCaptureActivity::class.java)

        assertTrue(entryPoint.requiresVideoModeOnly)
        assertFalse(entryPoint.isVideoOnlySession)
    }

    @Test
    fun aVideoOnlySession_recordsOnlyAndShowsNoModeTabs() {
        val entryPoint = entryPointFor(VideoOnlyActivity::class.java)

        assertTrue(entryPoint.isVideoOnlySession)
        assertTrue(entryPoint.requiresVideoModeOnly)
        assertFalse(entryPoint.showsCameraModeTabs)
    }
}
