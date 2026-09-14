package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CaptureUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SettingsSheetUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelTest {

    private val storedSettings = MutableStateFlow(CameraSettings())

    private val settingsRepository: SettingsRepository = mockk {
        every { settings } returns storedSettings
        coEvery { update(transform = any()) } answers {
            val transform = firstArg<(CameraSettings) -> CameraSettings>()
            storedSettings.value = transform(storedSettings.value)
            storedSettings.value
        }
    }

    private fun createViewModel(): ViewfinderViewModel {
        return ViewfinderViewModel(
            entryPoint = ENTRY_POINT,
            settingsRepository = settingsRepository,
            resolveAvailableModes = mockk(),
            resolveDroppedVideoQuality = mockk(),
            revertToMediaStoreLocation = mockk(),
            uiStateMapper = ViewfinderUiStateMapperImpl(
                settingsSheetUiStateMapper = SettingsSheetUiStateMapperImpl(),
                captureUiStateMapper = CaptureUiStateMapperImpl(),
            ),
        )
    }

    @Test
    fun perModeWrite_beforeAModeIsSlotted_isDropped() {
        val viewModel = createViewModel()

        viewModel.onAction(SettingsAction.GeoTaggingToggled(enabled = true))

        coVerify(exactly = 0) { settingsRepository.setGeoTagging(slot = any(), value = any()) }
    }

    @Test
    fun commonSettings_areReadThroughTheRepository() {
        val viewModel = createViewModel()

        storedSettings.value = CameraSettings(removeExifAfterCapture = false)

        viewModel.onAction(SettingsAction.GridToggleClicked)

        assertFalse(viewModel.uiState.value.capture.removeExifAfterCapture)
    }

    private companion object {
        val ENTRY_POINT = CameraEntryPoint(
            isSecureSession = false,
            isCaptureSession = false,
            isVideoOnlySession = false,
            requiresVideoModeOnly = false,
            allowsQrScanning = true,
            showsCameraModeTabs = true,
        )
    }
}
