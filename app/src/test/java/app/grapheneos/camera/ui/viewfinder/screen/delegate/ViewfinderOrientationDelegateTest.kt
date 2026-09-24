package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderOrientationDelegateTest {

    private val deviceOrientationRepository = mockk<DeviceOrientationRepository>()

    private val orientation = MutableSharedFlow<DeviceOrientation>()

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    @Test
    fun trackedOrientation_isPublishedToTheState() {
        runTest(UnconfinedTestDispatcher()) {
            every { deviceOrientationRepository.orientation() } returns orientation

            val delegate = ViewfinderOrientationDelegateImpl(
                deviceOrientationRepository = deviceOrientationRepository,
            )
            delegate.bind(stateHolder)
            backgroundScope.launch { delegate.trackOrientation() }

            orientation.emit(DeviceOrientation.DEGREES_270)

            assertEquals(DeviceOrientation.DEGREES_270, stateHolder.state.value.deviceOrientation)
        }
    }
}
