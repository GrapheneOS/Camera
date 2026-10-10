package app.grapheneos.camera.ui.camerasettings.screen

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.media.model.StorageLocationChange
import app.grapheneos.camera.domain.media.usecase.ChangeStorageLocation
import app.grapheneos.camera.domain.media.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.settingsRepositoryOver
import app.grapheneos.camera.ui.camerasettings.screen.mapper.CameraSettingsUiStateMapperImpl
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        isZslSupported: Boolean = false,
    ): CameraSettingsViewModel {
        val arguments = cameraSettingsArguments(
            isZslSupported = isZslSupported,
            photoResolutions = null,
        )

        return CameraSettingsViewModel(
            arguments = SavedStateHandle(
                arguments.keySet().associateWith { arguments.getBoolean(it) },
            ),
            entryPoint = entryPoint,
            settingsRepository = settingsRepositoryOver(storedSettings),
            capturedItemRepository = capturedItemRepository,
            changeStorageLocation = changeStorageLocation,
            revertToMediaStoreLocation = revertToMediaStoreLocation,
            uiStateMapper = CameraSettingsUiStateMapperImpl(),
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )
    }

    private companion object {
        val TREE: Uri = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADCIM",
        )
    }
}
