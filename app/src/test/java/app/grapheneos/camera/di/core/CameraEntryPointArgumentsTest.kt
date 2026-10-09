package app.grapheneos.camera.di.core

import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.testutil.cameraEntryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraEntryPointArgumentsTest {

    @Test
    fun eachFlag_isReadBackFromItsOwnKey() {
        listOf(
            cameraEntryPoint(isSecureSession = true),
            cameraEntryPoint(isCaptureSession = true),
            cameraEntryPoint(isVideoOnlySession = true),
            cameraEntryPoint(requiresVideoModeOnly = true),
            cameraEntryPoint(allowsQrScanning = false),
            cameraEntryPoint(showsCameraModeTabs = false),
        ).forEach { entryPoint ->
            val arguments = cameraEntryPointArguments(entryPoint)
            val handle = SavedStateHandle(
                arguments.keySet().associateWith { arguments.getBoolean(it) },
            )

            assertEquals(entryPoint, cameraEntryPointOf(handle))
        }
    }

    @Test
    fun missingArguments_areNotGuessed() {
        assertThrows(IllegalArgumentException::class.java) {
            cameraEntryPointOf(SavedStateHandle())
        }
    }
}
