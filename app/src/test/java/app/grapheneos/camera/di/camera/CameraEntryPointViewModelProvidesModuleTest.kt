package app.grapheneos.camera.di.camera

import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun cameraEntryPoint_missingFromTheArguments_isNotGuessed() {
        assertThrows(IllegalArgumentException::class.java) {
            module.provideCameraEntryPoint(SavedStateHandle())
        }
    }

    private companion object {
        val SECURE_ENTRY_POINT = cameraEntryPoint(
            isSecureSession = true,
            allowsQrScanning = false,
        )

        val CAPTURE_ENTRY_POINT = cameraEntryPoint(
            isCaptureSession = true,
            isVideoOnlySession = true,
            requiresVideoModeOnly = true,
            showsCameraModeTabs = false,
        )
    }
}
