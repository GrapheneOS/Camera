package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderEffects
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderCameraDelegateTest {

    private val environment = mockk<CameraSessionEnvironment>(relaxed = true)
    private val effects = mockk<ViewfinderEffects>(relaxed = true)
    private val chrome = mockk<ViewfinderChrome>(relaxed = true)
    private val session = mockk<CameraSession>(relaxed = true)

    private val emitted = mutableListOf<ViewfinderScreenEffect>()

    private var lensFacing = CameraSelector.LENS_FACING_BACK

    @Before
    fun setUp() {
        every { session.lensFacing } answers { lensFacing }
        every { session.lensFacing = any() } answers { lensFacing = firstArg() }
        every {
            session.isLensFacingSupported(lensFacing = any(), extensionMode = any())
        } returns true
        every { environment.isSessionActive } returns true
    }

    @Test
    fun beginBind_whileAlreadyBoundAndNotForced_leavesTheCameraAlone() {
        val delegate = createAttachedDelegate()
        val started = delegate.beginBind(forced = false)

        assertFalse(started)
        verify(exactly = 0) { effects.cancelPendingCapture() }
    }

    @Test
    fun beginBind_withoutACameraProvider_leavesTheCameraAloneEvenWhenForced() {
        every { session.cameraProvider } returns null

        val delegate = createAttachedDelegate()
        val started = delegate.beginBind(forced = true)

        assertFalse(started)
    }

    @Test
    fun selectLens_whileTheSessionIsInactive_selectsNothing() {
        every { environment.isSessionActive } returns false

        val delegate = createAttachedDelegate()
        val target = delegate.selectLens(isQrMode = false, extensionMode = ExtensionMode.NONE)

        assertNull(target)
        verify(exactly = 0) { session.selectLensFacing(any()) }
    }

    @Test
    fun selectLens_whenTheCurrentLensIsUnsupported_silentlyTakesTheOtherOne() {
        lensUnsupported(CameraSelector.LENS_FACING_BACK)

        val delegate = createAttachedDelegate()
        delegate.selectLens(isQrMode = false, extensionMode = ExtensionMode.NONE)

        verify(exactly = 1) { session.selectLensFacing(CameraSelector.LENS_FACING_FRONT) }
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun selectLens_forQrWithoutARearLens_scansWithTheFrontOneAndSaysSo() {
        lensUnsupported(CameraSelector.LENS_FACING_BACK)

        val delegate = createAttachedDelegate()
        val target = delegate.selectLens(isQrMode = true, extensionMode = ExtensionMode.NONE)

        assertEquals(CameraSelector.LENS_FACING_FRONT, target?.qrLensFacing)
        assertEquals(
            listOf(ViewfinderScreenEffect.ShowMessage(R.string.qr_rear_camera_unavailable)),
            emitted,
        )
    }

    @Test
    fun toggleLensFacing_toAnUnsupportedLens_revertsAndSaysSo() {
        lensUnsupported(CameraSelector.LENS_FACING_FRONT)

        val delegate = createAttachedDelegate()
        val switched = delegate.toggleLensFacing(extensionMode = ExtensionMode.NONE)

        assertFalse(switched)
        assertEquals(CameraSelector.LENS_FACING_BACK, lensFacing)
        assertEquals(
            listOf(ViewfinderScreenEffect.ShowMessage(R.string.front_camera_unavailable)),
            emitted,
        )
    }

    @Test
    fun toggleLensFacing_toASupportedLens_keepsIt() {
        val delegate = createAttachedDelegate()
        val switched = delegate.toggleLensFacing(extensionMode = ExtensionMode.NONE)

        assertTrue(switched)
        assertEquals(CameraSelector.LENS_FACING_FRONT, lensFacing)
    }

    @Test
    fun announceBind_withoutModeTabs_probesNoExtensions() {
        val delegate = createAttachedDelegate(showsCameraModeTabs = false)

        delegate.announceBind(
            aspectRatio = 0,
            isInPhotoMode = true,
            currentMode = { error("tabs are not shown") },
        )

        verify(exactly = 0) { session.probeUnknownExtensions(onRestart = any(), onSettled = any()) }
    }

    @Test
    fun detach_forgetsTheSession() {
        every { session.isFlashAvailable } returns true

        val delegate = createAttachedDelegate()
        delegate.detach()

        assertEquals(ViewfinderSessionState(), delegate.sessionState)
    }

    @Test
    fun isProviderReady_withoutAProvider_isFalse() {
        every { session.cameraProvider } returns null

        val delegate = createAttachedDelegate()

        assertFalse(delegate.isProviderReady)
    }

    @Test
    fun sessionEvents_areTheAttachedSessions() {
        val events = MutableSharedFlow<CameraSessionEvent>()
        every { session.events } returns events

        val delegate = createAttachedDelegate()

        assertSame(events, delegate.sessionEvents)
    }

    @Test
    fun applyFlashMode_isRememberedAcrossDetach() {
        val delegate = createAttachedDelegate()

        delegate.applyFlashMode(ImageCapture.FLASH_MODE_AUTO)
        delegate.detach()

        assertEquals(ImageCapture.FLASH_MODE_AUTO, delegate.flashMode)
    }

    private fun lensUnsupported(facing: Int) {
        every {
            session.isLensFacingSupported(
                lensFacing = facing,
                extensionMode = any(),
            )
        } returns false
    }

    private fun createAttachedDelegate(
        showsCameraModeTabs: Boolean = true,
    ): ViewfinderCameraDelegate {
        val delegate = ViewfinderCameraDelegateImpl(
            entryPoint = CameraEntryPoint(
                isSecureSession = false,
                isCaptureSession = false,
                isVideoOnlySession = false,
                requiresVideoModeOnly = false,
                allowsQrScanning = true,
                showsCameraModeTabs = showsCameraModeTabs,
            ),
            resolveAvailableModes = mockk(),
        )

        delegate.bind(
            ViewfinderStateHolder(
                initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
                render = { ViewfinderUiState() },
            ),
        )

        delegate.attach(
            environment = environment,
            effects = effects,
            chrome = chrome,
            session = session,
            emitEffect = { emitted += it },
        )

        return delegate
    }
}
