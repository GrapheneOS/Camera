package app.grapheneos.camera.ui.camerasettings.screen.mapper

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
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
    fun photoQuality_matchingALevel_isShownAsThatLevel() {
        val preset = mapper.map(CameraSettingsState(settings = CameraSettings(photoQuality = 75)))
        val custom = mapper.map(CameraSettingsState(settings = CameraSettings(photoQuality = 77)))

        assertEquals(PhotoQualityLevel.COMPACT, preset.photoQuality.level)
        assertNull(custom.photoQuality.level)
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
    fun zsl_isShownOnlyWhereSupported() {
        assertNull(mapper.map(CameraSettingsState(isZslSupported = false)).zsl)
        assertEquals(
            SwitchSettingUiModel(),
            mapper.map(CameraSettingsState(isZslSupported = true)).zsl,
        )
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
