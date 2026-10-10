package app.grapheneos.camera.ui.camerasettings.screen.mapper

import app.grapheneos.camera.data.camera.model.PhotoResolutions
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeOption
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.SwitchSettingUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraSettingsUiStateMapperTest {

    private val mapper = CameraSettingsUiStateMapperImpl()

    @Test
    fun defaults_areNotMarkedAsChanged() {
        val uiState = mapper.map(
            CameraSettingsState(storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION),
        )

        assertFalse(uiState.cameraSounds.isChanged)
        assertFalse(uiState.removeExif.isChanged)
        assertFalse(uiState.photoQuality.isChanged)
        assertEquals(false, uiState.storageLocation?.isChanged)
    }

    @Test
    fun valuesOtherThanTheDefault_areMarkedAsChanged() {
        val uiState = mapper.map(
            CameraSettingsState(
                settings = CameraSettings(
                    enableCameraSounds = false,
                    photoQuality = 80,
                ),
                storageLocation = "content://tree",
            ),
        )

        assertTrue(uiState.cameraSounds.isChanged)
        assertTrue(uiState.photoQuality.isChanged)
        assertEquals(true, uiState.storageLocation?.isChanged)
    }

    @Test
    fun photoQuality_matchingAPreset_isShownAsThatPreset() {
        val preset = mapper.map(CameraSettingsState(settings = CameraSettings(photoQuality = 90)))
        val custom = mapper.map(CameraSettingsState(settings = CameraSettings(photoQuality = 96)))

        assertEquals(PhotoQualityLevel.STANDARD, preset.photoQuality.level)
        assertEquals(PhotoQualityLevel.CUSTOM, custom.photoQuality.level)
    }

    @Test
    fun removeExif_inACaptureSession_isLockedOnWithoutAChangedMark() {
        val uiState = mapper.map(
            CameraSettingsState(
                settings = CameraSettings(removeExifAfterCapture = false),
                isCaptureSession = true,
            ),
        )

        assertEquals(
            SwitchSettingUiModel(
                checked = true,
                enabled = false,
            ),
            uiState.removeExif,
        )
    }

    @Test
    fun zeroShutterLag_isOfferedWhereSupportedOrAlreadyChosen() {
        val unsupported = mapper.map(CameraSettingsState(isZslSupported = false))
        val supported = mapper.map(CameraSettingsState(isZslSupported = true))
        val chosen = mapper.map(
            CameraSettingsState(
                settings = CameraSettings(captureMode = CaptureMode.ZERO_SHUTTER_LAG),
            ),
        )

        assertFalse(CaptureModeOption.ZERO_SHUTTER_LAG in unsupported.captureMode.options)
        assertTrue(CaptureModeOption.ZERO_SHUTTER_LAG in supported.captureMode.options)
        assertTrue(CaptureModeOption.ZERO_SHUTTER_LAG in chosen.captureMode.options)
    }

    @Test
    fun resolution_isOfferedOnlyWhereTheCameraHasMorePixelsToGive() {
        val withoutChoice = mapper.map(
            CameraSettingsState(settings = CameraSettings(selectHighestResolution = true)),
        )
        val withChoice = mapper.map(
            CameraSettingsState(
                settings = CameraSettings(selectHighestResolution = true),
                photoResolutions = PhotoResolutions(
                    standardMegapixels = 12,
                    fullMegapixels = 50,
                ),
            ),
        )

        assertNull(withoutChoice.photoQuality.resolution)
        assertTrue(withoutChoice.photoQuality.isFullResolution)
        assertFalse(withoutChoice.photoQuality.isChanged)
        assertEquals(
            PhotoResolutionUiModel(
                standardMegapixels = 12,
                fullMegapixels = 50,
            ),
            withChoice.photoQuality.resolution,
        )
        assertTrue(withChoice.photoQuality.isChanged)
    }

    @Test
    fun storage_inASecureSession_isHiddenBehindTheBanner() {
        val uiState = mapper.map(
            CameraSettingsState(
                storageLocation = "content://tree",
                isSecureSession = true,
            ),
        )

        assertTrue(uiState.showsSecureSessionBanner)
        assertNull(uiState.storageLocation)
    }
}
