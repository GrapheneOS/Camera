package app.grapheneos.camera.ui.viewfinder.screen

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CaptureUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SettingsSheetUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelTest {

    private val settingsRepository = RecordingSettingsRepository()

    private fun viewModel(): ViewfinderViewModel {
        return ViewfinderViewModel(
            entryPoint = ENTRY_POINT,
            settingsRepository = settingsRepository,
            resolveAvailableModes = NoModes(),
            resolveDroppedVideoQuality = NoDroppedQuality(),
            revertToMediaStoreLocation = NoRevert(),
            uiStateMapper = ViewfinderUiStateMapperImpl(
                settingsSheetUiStateMapper = SettingsSheetUiStateMapperImpl(),
                captureUiStateMapper = CaptureUiStateMapperImpl(),
            ),
        )
    }

    @Test
    fun perModeWrite_beforeAModeIsSlotted_isDropped() {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.GeoTaggingToggled(enabled = true))

        assertTrue(settingsRepository.geoTaggingWrites.isEmpty())
    }

    @Test
    fun commonSettings_areReadThroughTheRepository() {
        val viewModel = viewModel()

        settingsRepository.settings.value = CameraSettings(removeExifAfterCapture = false)

        viewModel.onAction(SettingsAction.GridToggleClicked)

        assertFalse(viewModel.uiState.value.capture.removeExifAfterCapture)
    }

    private class RecordingSettingsRepository : SettingsRepository {

        override val settings = MutableStateFlow(CameraSettings())

        val geoTaggingWrites = mutableListOf<Pair<ModeSlot, Boolean>>()

        override suspend fun update(
            transform: (CameraSettings) -> CameraSettings,
        ): CameraSettings {
            settings.value = transform(settings.value)

            return settings.value
        }

        override suspend fun modeSettings(slot: ModeSlot): ModeSettings {
            return ModeSettings()
        }

        override suspend fun setFlashMode(slot: ModeSlot, value: Int): ModeSettings {
            return ModeSettings(flashMode = value)
        }

        override suspend fun setGeoTagging(slot: ModeSlot, value: Boolean): ModeSettings {
            geoTaggingWrites.add(slot to value)

            return ModeSettings(geoTagging = value)
        }

        override suspend fun setSelfIllumination(slot: ModeSlot, value: Boolean): ModeSettings {
            return ModeSettings(selfIllumination = value)
        }

        override suspend fun setVideoQuality(slot: ModeSlot, value: Quality): ModeSettings {
            return ModeSettings(videoQuality = value)
        }
    }

    private class NoModes : ResolveAvailableModes {
        override fun invoke(
            allowsQrScanning: Boolean,
            extensionsAvailable: Boolean,
        ): Set<CameraMode> {
            return emptySet()
        }
    }

    private class NoDroppedQuality : ResolveDroppedVideoQuality {
        override fun invoke(
            lensFacing: Int,
            requestedQualityFeature: GroupableFeature?,
            selected: Set<GroupableFeature>,
        ): Quality? {
            return null
        }
    }

    private class NoRevert : RevertToMediaStoreLocation {
        override suspend fun invoke() = Unit
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
