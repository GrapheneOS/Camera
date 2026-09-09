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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderControllerTest {

    private val settingsRepository = RecordingSettingsRepository()

    private fun controller(): ViewfinderController {
        return ViewfinderController(
            entryPoint = ENTRY_POINT,
            settingsRepository = settingsRepository,
            resolveAvailableModes = NoModes(),
            resolveDroppedVideoQuality = NoDroppedQuality(),
            revertToMediaStoreLocation = NoRevert(),
        )
    }

    @Test
    fun perModeWrite_beforeAModeIsSlotted_isDropped() {
        val controller = controller()

        controller.videoQuality = Quality.UHD

        assertTrue(settingsRepository.videoQualityWrites.isEmpty())
    }

    @Test
    fun commonSettings_areReadThroughTheRepository() {
        val controller = controller()

        settingsRepository.settings.value = CameraSettings(photoQuality = SOME_PHOTO_QUALITY)

        assertEquals(SOME_PHOTO_QUALITY, controller.photoQuality)
    }

    private class RecordingSettingsRepository : SettingsRepository {

        override val settings = MutableStateFlow(CameraSettings())

        val videoQualityWrites = mutableListOf<Pair<ModeSlot, Quality>>()

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
            return ModeSettings(geoTagging = value)
        }

        override suspend fun setSelfIllumination(slot: ModeSlot, value: Boolean): ModeSettings {
            return ModeSettings(selfIllumination = value)
        }

        override suspend fun setVideoQuality(slot: ModeSlot, value: Quality): ModeSettings {
            videoQualityWrites.add(slot to value)

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

        const val SOME_PHOTO_QUALITY = 71
    }
}
