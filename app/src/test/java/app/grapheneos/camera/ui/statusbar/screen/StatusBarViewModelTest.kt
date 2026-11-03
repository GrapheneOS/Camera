package app.grapheneos.camera.ui.statusbar.screen

import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.statusbar.screen.mapper.StatusBarUiStateMapper
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarAction
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarNavEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusBarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun cameraSettingsClicked_emitsNavigationWithTheSessionAndCapabilities() {
        runTest {
            val session = mockk<CameraSession>(relaxed = true)
            val settings = mockk<SettingsRepository>()
            val entryPoint = cameraEntryPoint(isSecureSession = true)
            val capabilities = CameraCapabilities()
            every { session.boundCamera } returns MutableStateFlow(null)
            every { session.capabilities() } returns capabilities
            every { settings.settings } returns MutableStateFlow(CameraSettings())
            val viewModel = StatusBarViewModel(
                uiStateMapper = mockk<StatusBarUiStateMapper>(relaxed = true),
                entryPoint = entryPoint,
                settingsRepository = settings,
                session = session,
            )
            val events = mutableListOf<StatusBarNavEvent>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.navigationEvents.collect { events += it }
            }

            try {
                viewModel.onAction(StatusBarAction.CameraSettingsClicked)
                assertEquals(
                    listOf(
                        StatusBarNavEvent.OpenCameraSettings(
                            entryPoint = entryPoint,
                            capabilities = capabilities,
                        ),
                    ),
                    events,
                )
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }
    }
}
