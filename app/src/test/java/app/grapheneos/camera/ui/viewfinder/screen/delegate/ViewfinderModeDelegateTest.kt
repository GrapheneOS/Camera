package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.core.AspectRatio
import androidx.camera.extensions.ExtensionMode
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderModeDelegateTest {

    @Test
    fun currentMode_beforeAnySelection_isTheDefault() {
        val delegate = createDelegate()

        assertEquals(delegate.defaultMode, delegate.currentMode)
        assertTrue(delegate.isInPhotoMode)
    }

    @Test
    fun defaultMode_usesNoExtension() {
        val delegate = createDelegate()

        assertEquals(ExtensionMode.NONE, delegate.defaultMode.extensionMode)
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
        assertEquals(CameraMode.QR_SCAN, delegate.currentMode)
        assertTrue(delegate.isQrMode)
        assertFalse(delegate.isInPhotoMode)
    }

    @Test
    fun isVideoMode_inAVideoOnlyEntryPoint_holdsForEveryMode() {
        val delegate = createDelegate(requiresVideoModeOnly = true)

        assertTrue(delegate.isVideoMode)
    }

    @Test
    fun aspectRatio_inPhotoMode_isTheStoredOne() {
        val delegate = createDelegate()

        val aspectRatio = delegate.aspectRatio(storedAspectRatio = AspectRatio.RATIO_16_9)

        assertEquals(AspectRatio.RATIO_16_9, aspectRatio)
    }

    @Test
    fun aspectRatio_inVideoMode_isAlwaysWide() {
        val delegate = createDelegate()
        delegate.select(CameraMode.VIDEO)

        val aspectRatio = delegate.aspectRatio(storedAspectRatio = AspectRatio.RATIO_4_3)

        assertEquals(AspectRatio.RATIO_16_9, aspectRatio)
    }

    @Test
    fun aspectRatio_inQrMode_isAlwaysFourByThree() {
        val delegate = createDelegate()
        delegate.select(CameraMode.QR_SCAN)

        val aspectRatio = delegate.aspectRatio(storedAspectRatio = AspectRatio.RATIO_16_9)

        assertEquals(AspectRatio.RATIO_4_3, aspectRatio)
    }

    private fun createDelegate(requiresVideoModeOnly: Boolean = false): ViewfinderModeDelegate {
        val delegate = ViewfinderModeDelegateImpl(
            entryPoint = CameraEntryPoint(
                isSecureSession = false,
                isCaptureSession = false,
                isVideoOnlySession = requiresVideoModeOnly,
                requiresVideoModeOnly = requiresVideoModeOnly,
                allowsQrScanning = true,
                showsCameraModeTabs = true,
            ),
        )

        delegate.bind(
            ViewfinderStateHolder(
                initial = ViewfinderState(mode = delegate.defaultMode),
                render = { ViewfinderUiState() },
            ),
        )

        return delegate
    }
}
