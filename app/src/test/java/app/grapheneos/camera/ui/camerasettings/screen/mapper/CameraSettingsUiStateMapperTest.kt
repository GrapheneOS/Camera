package app.grapheneos.camera.ui.camerasettings.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.PhotoResolutions
import app.grapheneos.camera.data.camera.model.VideoBitrates
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.model.StorageSpace
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeOption
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.StorageSpaceUiModel
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
        assertFalse(uiState.grid.isChanged)
        assertFalse(uiState.photoQuality.isChanged)
        assertFalse(uiState.videoStabilization.isChanged)
        assertFalse(uiState.focusTimeout.isChanged)
        assertFalse(uiState.canResetSettings)
        assertEquals(false, uiState.storageLocation?.isChanged)
    }

    @Test
    fun valuesOtherThanTheDefault_areMarkedAsChanged() {
        val uiState = mapper.map(
            CameraSettingsState(
                settings = CameraSettings(
                    gridType = GridType.GOLDEN_RATIO,
                    focusTimeoutSeconds = 10,
                    enableCameraSounds = false,
                    enableEis = false,
                    photoQuality = 80,
                ),
                storageLocation = "content://tree",
            ),
        )

        assertTrue(uiState.cameraSounds.isChanged)
        assertTrue(uiState.grid.isChanged)
        assertTrue(uiState.photoQuality.isChanged)
        assertTrue(uiState.videoStabilization.isChanged)
        assertTrue(uiState.focusTimeout.isChanged)
        assertTrue(uiState.canResetSettings)
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
    fun geoTagging_withoutPermission_keepsItsValueAndIsLockedOnlyInASecureSession() {
        val settings = CameraSettings(geoTagging = true)
        val granted = mapper.map(
            CameraSettingsState(
                settings = settings,
                isLocationPermissionGranted = true,
            ),
        )
        val revoked = mapper.map(CameraSettingsState(settings = settings))
        val lockscreen = mapper.map(
            CameraSettingsState(
                settings = settings,
                isSecureSession = true,
            ),
        )

        val on = SwitchSettingUiModel(checked = true, isChanged = true)

        assertEquals(on, granted.geoTagging)
        assertEquals(on, revoked.geoTagging)
        assertEquals(on.copy(enabled = false), lockscreen.geoTagging)
        assertFalse(granted.offersLocationSettings)
        assertTrue(revoked.offersLocationSettings)
        assertFalse(lockscreen.offersLocationSettings)
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
        val unsupported = mapper.map(
            CameraSettingsState(capabilities = CameraCapabilities(isZslSupported = false)),
        )
        val supported = mapper.map(
            CameraSettingsState(capabilities = CameraCapabilities(isZslSupported = true)),
        )
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
                capabilities = CameraCapabilities(
                    photoResolutions = PhotoResolutions(
                        standardMegapixels = 12,
                        fullMegapixels = 50,
                    ),
                ),
            ),
        )

        assertNull(withoutChoice.photoQuality.resolution)
        assertEquals(
            PhotoResolutionUiModel(
                standardMegapixels = 12,
                fullMegapixels = 50,
            ),
            withChoice.photoQuality.resolution,
        )
    }

    @Test
    fun fullResolution_withoutAChoice_isStillMarkedAsChanged() {
        val uiState = mapper.map(
            CameraSettingsState(settings = CameraSettings(selectHighestResolution = true)),
        )

        assertTrue(uiState.photoQuality.isFullResolution)
        assertTrue(uiState.photoQuality.isChanged)
    }

    @Test
    fun videoStabilization_onACameraThatCannotStabilize_isLockedAtItsStoredValue() {
        val unsupported = mapper.map(
            CameraSettingsState(settings = CameraSettings(enableEis = false)),
        )
        val supported = mapper.map(
            CameraSettingsState(
                settings = CameraSettings(enableEis = false),
                capabilities = CameraCapabilities(isVideoStabilizationSupported = true),
            ),
        )

        assertFalse(unsupported.videoStabilization.enabled)
        assertFalse(unsupported.videoStabilization.checked)
        assertTrue(supported.videoStabilization.enabled)
    }

    @Test
    fun focusTimeout_storedOutsideTheChoices_isStillOffered() {
        val uiState = mapper.map(
            CameraSettingsState(settings = CameraSettings(focusTimeoutSeconds = 7)),
        )

        assertEquals(listOf(0L, 3L, 5L, 7L, 8L, 10L), uiState.focusTimeout.options)
        assertEquals(7L, uiState.focusTimeout.selected)
    }

    @Test
    fun storageSpace_isEstimatedFromTheChosenQualitiesAndTheBitrateCameraXRecordsAt() {
        val uiState = mapper.map(
            CameraSettingsState(
                storageSpace = StorageSpace(
                    freeBytes = 1_200_000_000L,
                    totalBytes = 4_800_000_000L,
                ),
                isStorageSpaceLoaded = true,
                capabilities = CameraCapabilities(
                    photoMegapixels = 12,
                    videoBitrates = mapOf(
                        VideoQuality.FHD to VideoBitrates(
                            videoBitsPerSecond = 31_808_000,
                            videoFrameRate = 60,
                            audioBitsPerSecond = 96_000,
                        ),
                    ),
                ),
                settings = CameraSettings(photoQuality = 95),
                videoQuality = VideoQuality.FHD,
            ),
        )

        assertEquals(
            StorageSpaceUiModel(
                freeBytes = 1_200_000_000L,
                usedFraction = 0.75f,
                photos = 320,
                videoMinutes = 10,
            ),
            uiState.storageSpace,
        )
    }

    @Test
    fun storageSpace_untilItIsRead_isShownAsLoading() {
        val uiState = mapper.map(CameraSettingsState())

        assertEquals(StorageSpaceUiModel(), uiState.storageSpace)
    }

    @Test
    fun storageSpace_ofALocationOffTheDevicesStorage_isHidden() {
        val uiState = mapper.map(CameraSettingsState(isStorageSpaceLoaded = true))

        assertNull(uiState.storageSpace)
    }

    @Test
    fun storageSpace_atFullResolution_isEstimatedForItsPixels() {
        val uiState = mapper.map(
            CameraSettingsState(
                storageSpace = StorageSpace(
                    freeBytes = 1_200_000_000L,
                    totalBytes = 4_800_000_000L,
                ),
                isStorageSpaceLoaded = true,
                capabilities = CameraCapabilities(
                    photoMegapixels = 12,
                    photoResolutions = PhotoResolutions(
                        standardMegapixels = 12,
                        fullMegapixels = 50,
                    ),
                ),
                settings = CameraSettings(
                    photoQuality = 95,
                    selectHighestResolution = true,
                ),
            ),
        )

        assertEquals(76L, uiState.storageSpace?.photos)
        assertNull(uiState.storageSpace?.videoMinutes)
    }

    @Test
    fun storageSpace_inASecureSession_isHidden() {
        val uiState = mapper.map(CameraSettingsState(isSecureSession = true))

        assertNull(uiState.storageSpace)
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

    @Test
    fun licenses_areOfferedOutsideASecureSessionOnly() {
        val unlocked = mapper.map(CameraSettingsState())
        val lockscreen = mapper.map(CameraSettingsState(isSecureSession = true))

        assertTrue(unlocked.showsLicenses)
        assertFalse(lockscreen.showsLicenses)
    }
}
