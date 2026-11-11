package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraControl
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

interface SceneLightControl {

    fun attach(
        control: CameraControl,
        boundCamera: BoundCamera,
    )

    fun detach()

    /** Keeps the video light or low light boost on while the flash mode asks for it. */
    fun setFlashMode(flashMode: FlashMode)
}

internal class SceneLightControlImpl @Inject constructor(
    @MainImmediateDispatcher mainDispatcher: CoroutineDispatcher,
) : SceneLightControl {

    private val mainExecutor: Executor = mainDispatcher.asExecutor()
    private var attachment: Attachment? = null

    override fun attach(
        control: CameraControl,
        boundCamera: BoundCamera,
    ) {
        attachment = Attachment(
            control = control,
            boundCamera = boundCamera,
        )
    }

    override fun detach() {
        attachment = null
    }

    override fun setFlashMode(flashMode: FlashMode) {
        val attached = attachment ?: return
        val previousLight = attached.light
        val light = lightFor(
            flashMode = flashMode,
            boundCamera = attached.boundCamera,
        )

        if (light == previousLight) return

        attached.light = light

        // Switching low light boost turns the torch off, and CameraX refuses to switch the torch
        // again until the boost is off.
        when {
            light == Light.LOW_LIGHT_BOOST -> {
                attached.control.enableLowLightBoostAsync(true)
            }

            previousLight == Light.LOW_LIGHT_BOOST -> {
                val request = attached.control.enableLowLightBoostAsync(false)
                request.addListener({ onLowLightBoostOff(attached) }, mainExecutor)
            }

            else -> attached.control.enableTorch(light == Light.VIDEO_LIGHT)
        }
    }

    private fun lightFor(
        flashMode: FlashMode,
        boundCamera: BoundCamera,
    ): Light {
        return when (flashMode) {
            !in boundCamera.flashModes() -> Light.NONE
            FlashMode.LOW_LIGHT_BOOST -> Light.LOW_LIGHT_BOOST
            FlashMode.ON if boundCamera.isVideoMode -> Light.VIDEO_LIGHT
            else -> Light.NONE
        }
    }

    // CameraX posts the boost's off state to the main thread before it completes the request, so
    // this listener runs after it.
    private fun onLowLightBoostOff(attached: Attachment) {
        if (attachment !== attached || attached.light != Light.VIDEO_LIGHT) return

        attached.control.enableTorch(true)
    }

    private enum class Light {
        NONE,
        VIDEO_LIGHT,
        LOW_LIGHT_BOOST,
    }

    private class Attachment(
        val control: CameraControl,
        val boundCamera: BoundCamera,
    ) {
        var light = Light.NONE
    }
}
