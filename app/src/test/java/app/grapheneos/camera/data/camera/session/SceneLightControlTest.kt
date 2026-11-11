package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraControl
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.testutil.MainDispatcherRule
import com.google.common.util.concurrent.SettableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Rule
import org.junit.Test

class SceneLightControlTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Suppress("ForbiddenVoid")
    private val lowLightBoostOff = SettableFuture.create<Void?>()
    private val cameraControl = mockk<CameraControl>(relaxed = true) {
        every { enableLowLightBoostAsync(true) } returns SettableFuture.create()
        every { enableLowLightBoostAsync(false) } returns lowLightBoostOff
    }
    private val sceneLightControl = SceneLightControlImpl(
        mainDispatcher = mainDispatcherRule.testDispatcher,
    )

    @Test
    fun videoLight_switchesTheTorchWithoutTouchingLowLightBoost() {
        attach(VIDEO_CAMERA)

        sceneLightControl.setFlashMode(FlashMode.ON)
        sceneLightControl.setFlashMode(FlashMode.OFF)

        verifyOrder {
            cameraControl.enableTorch(true)
            cameraControl.enableTorch(false)
        }
        verify(exactly = 0) { cameraControl.enableLowLightBoostAsync(any()) }
    }

    @Test
    fun videoLight_afterLowLightBoost_waitsForTheBoostToGoOff() {
        attach(VIDEO_CAMERA)
        sceneLightControl.setFlashMode(FlashMode.LOW_LIGHT_BOOST)

        sceneLightControl.setFlashMode(FlashMode.ON)
        verify(exactly = 0) { cameraControl.enableTorch(any()) }
        lowLightBoostOff.set(null)

        verify(exactly = 1) { cameraControl.enableTorch(true) }
    }

    @Test
    fun videoLight_turnedOffWhileTheBoostGoesOff_staysOff() {
        attach(VIDEO_CAMERA)
        sceneLightControl.setFlashMode(FlashMode.LOW_LIGHT_BOOST)

        sceneLightControl.setFlashMode(FlashMode.ON)
        sceneLightControl.setFlashMode(FlashMode.OFF)
        lowLightBoostOff.set(null)

        verify(exactly = 0) { cameraControl.enableTorch(true) }
    }

    @Test
    fun videoLight_detachedWhileTheBoostGoesOff_staysOff() {
        attach(VIDEO_CAMERA)
        sceneLightControl.setFlashMode(FlashMode.LOW_LIGHT_BOOST)

        sceneLightControl.setFlashMode(FlashMode.ON)
        sceneLightControl.detach()
        lowLightBoostOff.set(null)

        verify(exactly = 0) { cameraControl.enableTorch(true) }
    }

    @Test
    fun lowLightBoost_isRequestedOnlyWhenItChanges() {
        attach(VIDEO_CAMERA)

        sceneLightControl.setFlashMode(FlashMode.LOW_LIGHT_BOOST)
        sceneLightControl.setFlashMode(FlashMode.LOW_LIGHT_BOOST)
        sceneLightControl.setFlashMode(FlashMode.OFF)
        sceneLightControl.setFlashMode(FlashMode.OFF)

        verify(exactly = 1) { cameraControl.enableLowLightBoostAsync(true) }
        verify(exactly = 1) { cameraControl.enableLowLightBoostAsync(false) }
    }

    @Test
    fun flashOn_inPhotoMode_leavesTheTorchAlone() {
        attach(PHOTO_CAMERA)

        sceneLightControl.setFlashMode(FlashMode.ON)

        verify(exactly = 0) { cameraControl.enableTorch(any()) }
    }

    @Test
    fun modesTheCameraDoesNotOffer_leaveTheSceneUnlit() {
        attach(VIDEO_CAMERA.copy(hasFlashUnit = false, isLowLightBoostSupported = false))

        sceneLightControl.setFlashMode(FlashMode.ON)
        sceneLightControl.setFlashMode(FlashMode.LOW_LIGHT_BOOST)

        verify(exactly = 0) { cameraControl.enableTorch(any()) }
        verify(exactly = 0) { cameraControl.enableLowLightBoostAsync(any()) }
    }

    @Test
    fun reattached_lightsTheVideoAgain() {
        attach(VIDEO_CAMERA)
        sceneLightControl.setFlashMode(FlashMode.ON)

        attach(VIDEO_CAMERA)
        sceneLightControl.setFlashMode(FlashMode.ON)

        verify(exactly = 2) { cameraControl.enableTorch(true) }
    }

    private fun attach(boundCamera: BoundCamera) {
        sceneLightControl.attach(
            control = cameraControl,
            boundCamera = boundCamera,
        )
    }

    private companion object {
        val VIDEO_CAMERA = BoundCamera(
            mode = CameraMode.VIDEO,
            isVideoMode = true,
            lensFacing = LensFacing.BACK,
            hasFlashUnit = true,
            videoQualities = emptyList(),
            isLowLightBoostSupported = true,
        )

        val PHOTO_CAMERA = VIDEO_CAMERA.copy(
            mode = CameraMode.CAMERA,
            isVideoMode = false,
        )
    }
}
