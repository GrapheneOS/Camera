package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.viewfinderStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderModeDelegateTest {

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun defaultMode_forPhotoEntryPoints_isCameraWithoutAnExtension() {
        val regular = createDelegate()
        val capture = createDelegate(entryPoint = cameraEntryPoint(isCaptureSession = true))

        assertEquals(CameraMode.CAMERA, regular.defaultMode)
        assertEquals(CameraMode.CAMERA, capture.defaultMode)
        assertNull(regular.defaultMode.extensionMode)
        assertNull(capture.defaultMode.extensionMode)
    }

    @Test
    fun defaultMode_forTheVideoShortcut_isVideoWithoutAnExtension() {
        val delegate = createDelegate(
            entryPoint = cameraEntryPoint(
                isVideoOnlySession = true,
                requiresVideoModeOnly = true,
            ),
        )

        assertEquals(CameraMode.VIDEO, delegate.defaultMode)
        assertNull(delegate.defaultMode.extensionMode)
    }

    @Test
    fun defaultMode_forVideoCapture_isVideoWithoutAnExtension() {
        val delegate = createDelegate(
            entryPoint = cameraEntryPoint(
                isCaptureSession = true,
                requiresVideoModeOnly = true,
            ),
        )

        assertEquals(CameraMode.VIDEO, delegate.defaultMode)
        assertNull(delegate.defaultMode.extensionMode)
    }

    @Test
    fun select_theModeAlreadyCurrent_reportsNoChange() {
        val delegate = createDelegate()

        val changed = delegate.select(delegate.defaultMode)

        assertFalse(changed)
    }

    @Test
    fun select_anotherMode_makesItCurrent() {
        val delegate = createDelegate()

        val changed = delegate.select(CameraMode.QR_SCAN)

        assertTrue(changed)
        assertEquals(CameraMode.QR_SCAN, stateHolder.state.value.mode)
    }

    private fun createDelegate(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
    ): ViewfinderModeDelegate {
        val delegate = ViewfinderModeDelegateImpl(entryPoint = entryPoint)

        delegate.bind(stateHolder)

        return delegate
    }
}
