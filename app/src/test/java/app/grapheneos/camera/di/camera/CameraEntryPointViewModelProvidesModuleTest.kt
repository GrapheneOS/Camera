package app.grapheneos.camera.di.camera

import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraEntryPointViewModelProvidesModuleTest {

    private val module = CameraEntryPointViewModelProvidesModule()

    @Test
    fun cameraEntryPoint_readsBackWhatTheActivityPutInTheArguments() {
        listOf(SECURE_ENTRY_POINT, CAPTURE_ENTRY_POINT).forEach { entryPoint ->
            val arguments = ViewfinderViewModel.arguments(entryPoint)
            val handle = SavedStateHandle(
                arguments.keySet().associateWith { arguments.getBoolean(it) },
            )

            assertEquals(entryPoint, module.provideCameraEntryPoint(handle))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun cameraEntryPoint_missingFromTheArguments_isNotGuessed() {
        module.provideCameraEntryPoint(SavedStateHandle())
    }

    private companion object {
        val SECURE_ENTRY_POINT = CameraEntryPoint(
            isSecureSession = true,
            isCaptureSession = false,
            isVideoOnlySession = false,
            requiresVideoModeOnly = false,
            allowsQrScanning = false,
            showsCameraModeTabs = true,
        )

        val CAPTURE_ENTRY_POINT = CameraEntryPoint(
            isSecureSession = false,
            isCaptureSession = true,
            isVideoOnlySession = true,
            requiresVideoModeOnly = true,
            allowsQrScanning = true,
            showsCameraModeTabs = false,
        )
    }
}
