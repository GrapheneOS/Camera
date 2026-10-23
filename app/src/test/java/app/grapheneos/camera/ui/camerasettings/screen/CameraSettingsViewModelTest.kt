package app.grapheneos.camera.ui.camerasettings.screen

import android.net.Uri
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.media.model.StorageSpace
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.media.repository.StorageSpaceRepository
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.media.model.StorageLocationChange
import app.grapheneos.camera.domain.media.usecase.ChangeStorageLocation
import app.grapheneos.camera.domain.media.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.settingsRepositoryOver
import app.grapheneos.camera.ui.camerasettings.screen.mapper.CameraSettingsUiStateMapperImpl
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsScreenEffect as Effect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storedSettings = MutableStateFlow(CameraSettings())
    private val storageLocation = MutableStateFlow(CapturedItemRepository.MEDIA_STORE_LOCATION)
    private val capturedItemRepository = mockk<CapturedItemRepository> {
        every { storageLocation } returns this@CameraSettingsViewModelTest.storageLocation
    }
    private val changeStorageLocation = mockk<ChangeStorageLocation>()
    private val revertToMediaStoreLocation = mockk<RevertToMediaStoreLocation>(relaxed = true)
    private val storageSpaceRepository = mockk<StorageSpaceRepository> {
        coEvery { storageSpace(any()) } returns STORAGE_SPACE
    }
    private var isLocationPermissionGranted = false
    private val permissionRepository = mockk<PermissionRepository> {
        every { isGranted(AppPermission.LOCATION) } answers { isLocationPermissionGranted }
    }

    @Test
    fun switchToggled_storesTheSetting() {
        val viewModel = createViewModel()

        viewModel.onAction(Action.CameraSoundsToggled(enabled = false))

        assertEquals(false, storedSettings.value.enableCameraSounds)
        assertEquals(false, viewModel.uiState.value.cameraSounds.checked)
    }

    @Test
    fun settingChangedElsewhere_reachesTheScreen() {
        val viewModel = createViewModel()

        storedSettings.value = storedSettings.value.copy(gyroscopeSuggestions = true)

        assertTrue(viewModel.uiState.value.gyroscopeSuggestions.checked)
    }

    @Test
    fun geoTaggingToggledOn_withoutPermission_asksForItBeforeStoringAnything() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(Action.GeoTaggingToggled(enabled = true))

            assertEquals(Effect.RequestLocationPermission, viewModel.effects.first())
            assertFalse(storedSettings.value.geoTagging)
        }
    }

    @Test
    fun locationPermissionAnswered_turnsGeoTaggingOnOnlyWithAGrant() {
        val viewModel = createViewModel()

        viewModel.onAction(Action.LocationPermissionAnswered)
        assertFalse(storedSettings.value.geoTagging)

        isLocationPermissionGranted = true
        viewModel.onAction(Action.LocationPermissionAnswered)
        assertTrue(storedSettings.value.geoTagging)
    }

    @Test
    fun locationSettingsClicked_opensTheAppSettings() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(Action.LocationSettingsClicked)

            assertEquals(Effect.OpenAppSettings, viewModel.effects.first())
        }
    }

    @Test
    fun screenResumed_afterThePermissionWasGrantedElsewhere_storesTheToggleRightAway() {
        val viewModel = createViewModel()

        isLocationPermissionGranted = true
        viewModel.onAction(Action.ScreenResumed)
        viewModel.onAction(Action.GeoTaggingToggled(enabled = true))

        assertTrue(storedSettings.value.geoTagging)
    }

    @Test
    fun removeExifToggled_inACaptureSession_leavesTheOwnersSettingAlone() {
        val viewModel = createViewModel(
            entryPoint = cameraEntryPoint(isCaptureSession = true),
        )

        viewModel.onAction(Action.RemoveExifToggled(enabled = false))

        assertEquals(true, storedSettings.value.removeExifAfterCapture)
    }

    @Test
    fun photoQualityConfirmed_inRange_isStoredAndClosesTheDialog() {
        val viewModel = createViewModel()

        viewModel.onAction(Action.PhotoQualityClicked)
        viewModel.onAction(
            Action.PhotoQualityConfirmed(
                quality = 77,
                isFullResolution = true,
            ),
        )

        assertEquals(77, storedSettings.value.photoQuality)
        assertEquals(true, storedSettings.value.selectHighestResolution)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun photoQualityConfirmed_outOfRange_neverReachesTheStoredSettings() {
        val viewModel = createViewModel()
        val stored = storedSettings.value.photoQuality

        listOf(0, 101).forEach { quality ->
            viewModel.onAction(
                Action.PhotoQualityConfirmed(
                    quality = quality,
                    isFullResolution = false,
                ),
            )
        }

        assertEquals(stored, storedSettings.value.photoQuality)
    }

    @Test
    fun captureModeConfirmed_isStoredAndClosesTheDialog() {
        val viewModel = createViewModel()

        viewModel.onAction(Action.CaptureModeClicked)
        viewModel.onAction(Action.CaptureModeConfirmed(CaptureMode.QUALITY))

        assertEquals(CaptureMode.QUALITY, storedSettings.value.captureMode)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun gridConfirmed_isStoredAndClosesTheDialog() {
        val viewModel = createViewModel()

        viewModel.onAction(Action.GridClicked)
        viewModel.onAction(Action.GridConfirmed(GridType.FOUR_BY_FOUR))

        assertEquals(GridType.FOUR_BY_FOUR, storedSettings.value.gridType)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun focusTimeoutConfirmed_isStoredAndClosesTheDialog() {
        val viewModel = createViewModel()

        viewModel.onAction(Action.FocusTimeoutClicked)
        viewModel.onAction(Action.FocusTimeoutConfirmed(seconds = 0))

        assertEquals(0L, storedSettings.value.focusTimeoutSeconds)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun volumeKeyActionConfirmed_isStoredAndClosesTheDialog() {
        val viewModel = createViewModel()

        viewModel.onAction(Action.VolumeKeyActionClicked)
        viewModel.onAction(Action.VolumeKeyActionConfirmed(VolumeKeyAction.VOLUME))

        assertEquals(VolumeKeyAction.VOLUME, storedSettings.value.volumeKeyAction)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun resetSettingsConfirmed_resetsThisScreenOnly() {
        storedSettings.value = CameraSettings(
            aspectRatio = AspectRatio.RATIO_16_9,
            gridType = GridType.GOLDEN_RATIO,
            photoQuality = 80,
            geoTagging = true,
        )
        val viewModel = createViewModel()

        viewModel.onAction(Action.ResetSettingsClicked)
        viewModel.onAction(Action.ResetSettingsConfirmed)

        assertEquals(CameraSettings(aspectRatio = AspectRatio.RATIO_16_9), storedSettings.value)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun resetSettingsConfirmed_inACaptureSession_leavesTheOwnersExifSettingAlone() {
        storedSettings.value = CameraSettings(removeExifAfterCapture = false)
        val viewModel = createViewModel(
            entryPoint = cameraEntryPoint(isCaptureSession = true),
        )

        viewModel.onAction(Action.ResetSettingsConfirmed)

        assertFalse(storedSettings.value.removeExifAfterCapture)
    }

    @Test
    fun storageSpace_ofTheStorageLocation_reachesTheScreen() {
        val viewModel = createViewModel()

        assertEquals(STORAGE_SPACE.freeBytes, viewModel.uiState.value.storageSpace?.freeBytes)
        coVerify {
            storageSpaceRepository.storageSpace(CapturedItemRepository.MEDIA_STORE_LOCATION)
        }
    }

    @Test
    fun storageSpace_inASecureSession_isNotRead() {
        createViewModel(entryPoint = cameraEntryPoint(isSecureSession = true))

        coVerify(exactly = 0) { storageSpaceRepository.storageSpace(any()) }
    }

    @Test
    fun storageLocationPicked_nothing_leavesTheLocationAlone() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(Action.StorageLocationPicked(tree = null))

            coVerify(exactly = 0) { changeStorageLocation(any()) }
        }
    }

    @Test
    fun storageLocationPicked_aTree_becomesTheLocation() {
        runTest {
            coEvery { changeStorageLocation(TREE) } returns StorageLocationChange.Changed(
                location = TREE.toString(),
            )
            val viewModel = createViewModel()

            viewModel.onAction(Action.StorageLocationPicked(tree = TREE))

            coVerify { changeStorageLocation(TREE) }
        }
    }

    @Test
    fun storageLocationResetClicked_revertsWithoutAsking() {
        runTest {
            storageLocation.value = TREE.toString()
            val viewModel = createViewModel()

            viewModel.onAction(Action.StorageLocationResetClicked)

            coVerify { revertToMediaStoreLocation() }
            assertNull(viewModel.uiState.value.dialog)
        }
    }

    private fun createViewModel(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
    ): CameraSettingsViewModel {
        val arguments = cameraSettingsArguments(CameraCapabilities())

        return CameraSettingsViewModel(
            arguments = SavedStateHandle(
                arguments.keySet().associateWith { key ->
                    BundleCompat.getParcelable(arguments, key, CameraCapabilities::class.java)
                },
            ),
            entryPoint = entryPoint,
            settingsRepository = settingsRepositoryOver(storedSettings),
            permissionRepository = permissionRepository,
            capturedItemRepository = capturedItemRepository,
            storageSpaceRepository = storageSpaceRepository,
            changeStorageLocation = changeStorageLocation,
            revertToMediaStoreLocation = revertToMediaStoreLocation,
            uiStateMapper = CameraSettingsUiStateMapperImpl(),
        )
    }

    private companion object {
        val STORAGE_SPACE = StorageSpace(
            freeBytes = 1_000_000_000L,
            totalBytes = 4_000_000_000L,
        )
        val TREE: Uri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADCIM",
        )
    }
}
