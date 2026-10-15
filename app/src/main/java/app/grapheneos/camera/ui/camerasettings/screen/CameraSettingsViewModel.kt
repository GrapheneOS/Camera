package app.grapheneos.camera.ui.camerasettings.screen

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.media.repository.StorageSpaceRepository
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.media.usecase.ChangeStorageLocation
import app.grapheneos.camera.domain.media.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.ui.camerasettings.screen.mapper.CameraSettingsUiStateMapper
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsDialog as Dialog
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsScreenEffect as Effect
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal interface CameraSettingsScreenModel {

    val uiState: StateFlow<CameraSettingsUiState>

    val effects: Flow<Effect>

    fun onAction(action: Action)
}

@HiltViewModel
internal class CameraSettingsViewModel @Inject constructor(
    arguments: SavedStateHandle,
    entryPoint: CameraEntryPoint,
    private val settingsRepository: SettingsRepository,
    private val permissionRepository: PermissionRepository,
    private val capturedItemRepository: CapturedItemRepository,
    private val storageSpaceRepository: StorageSpaceRepository,
    private val changeStorageLocation: ChangeStorageLocation,
    private val revertToMediaStoreLocation: RevertToMediaStoreLocation,
    private val uiStateMapper: CameraSettingsUiStateMapper,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel(),
    CameraSettingsScreenModel {

    private var state = cameraCapabilitiesOf(arguments).let { capabilities ->
        CameraSettingsState(
            isSecureSession = entryPoint.isSecureSession,
            isCaptureSession = entryPoint.isCaptureSession,
            capabilities = capabilities,
            isLocationPermissionGranted = permissionRepository.isGranted(AppPermission.LOCATION),
            settings = settingsRepository.settings.value,
            videoQuality = videoModeQuality(capabilities.lensFacing),
        )
    }

    private val _uiState = MutableStateFlow(uiStateMapper.map(state))
    override val uiState: StateFlow<CameraSettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch(mainDispatcher) {
            settingsRepository.settings.collect { settings ->
                update { it.copy(settings = settings) }
            }
        }

        viewModelScope.launch(mainDispatcher) {
            capturedItemRepository.storageLocation.collect { location ->
                update { it.copy(storageLocation = location) }
                refreshStorageSpace()
            }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.ScreenResumed -> onScreenResumed()
            is Action.LocationPermissionAnswered -> onLocationPermissionAnswered()
            is Action.LocationSettingsClicked -> _effects.trySend(Effect.OpenAppSettings)
            is Action.DialogSettingClicked -> showDialog(action.dialog)
            is Action.SwitchToggled -> onSwitchToggled(action)
            is Action.DialogConfirmed -> onDialogConfirmed(action)
            is Action.DialogDismissed -> showDialog(null)
            is Action.StorageLocationClicked -> _effects.trySend(Effect.PickStorageLocation)
            is Action.StorageLocationPicked -> changeStorageLocation(action.tree)
            is Action.StorageLocationResetClicked -> resetStorageLocation()
            is Action.LicensesClicked -> _effects.trySend(Effect.OpenLicenses)
        }
    }

    private fun onScreenResumed() {
        refreshLocationPermission()
        refreshStorageSpace()
    }

    private fun refreshLocationPermission() {
        val isGranted = permissionRepository.isGranted(AppPermission.LOCATION)

        update { it.copy(isLocationPermissionGranted = isGranted) }
    }

    private fun onLocationPermissionAnswered() {
        refreshLocationPermission()

        updateSettings { it.copy(geoTagging = state.isLocationPermissionGranted) }
    }

    private fun onSwitchToggled(action: Action.SwitchToggled) {
        val enabled = action.enabled

        when (action) {
            is Action.GeoTaggingToggled -> setGeoTagging(enabled)
            is Action.RemoveExifToggled -> setRemoveExif(enabled)

            is Action.GyroscopeSuggestionsToggled -> {
                updateSettings { it.copy(gyroscopeSuggestions = enabled) }
            }

            is Action.KeepSelfiesAsSeenToggled -> {
                updateSettings { it.copy(keepSelfiesAsSeen = enabled) }
            }

            is Action.VideoStabilizationToggled -> {
                updateSettings { it.copy(enableEis = enabled) }
            }

            is Action.CameraSoundsToggled -> {
                updateSettings { it.copy(enableCameraSounds = enabled) }
            }

            is Action.TimerLightToggled -> {
                updateSettings { it.copy(timerLight = enabled) }
            }
        }
    }

    private fun setGeoTagging(enabled: Boolean) {
        when {
            enabled && !state.isLocationPermissionGranted -> {
                _effects.trySend(Effect.RequestLocationPermission)
            }

            else -> updateSettings { it.copy(geoTagging = enabled) }
        }
    }

    private fun setRemoveExif(enabled: Boolean) {
        if (state.isCaptureSession) return

        updateSettings { it.copy(removeExifAfterCapture = enabled) }
    }

    private fun onDialogConfirmed(action: Action.DialogConfirmed) {
        showDialog(null)

        when (action) {
            is Action.GridConfirmed -> {
                updateSettings { it.copy(gridType = action.type) }
            }

            is Action.PhotoQualityConfirmed -> {
                confirmPhotoQuality(action)
            }

            is Action.CaptureModeConfirmed -> {
                updateSettings { it.copy(captureMode = action.mode) }
            }

            is Action.FocusTimeoutConfirmed -> {
                updateSettings { it.copy(focusTimeoutSeconds = action.seconds) }
            }

            is Action.VolumeKeyActionConfirmed -> {
                updateSettings { it.copy(volumeKeyAction = action.action) }
            }

            is Action.ResetSettingsConfirmed -> {
                updateSettings { state.settingsAfterReset() }
            }
        }
    }

    private fun confirmPhotoQuality(action: Action.PhotoQualityConfirmed) {
        updateSettings { settings ->
            settings.copy(
                photoQuality = action.quality
                    .takeIf { it in CameraSettings.PHOTO_QUALITY_RANGE }
                    ?: settings.photoQuality,
                selectHighestResolution = action.isFullResolution,
            )
        }
    }

    private fun changeStorageLocation(tree: Uri?) {
        if (tree == null) return

        viewModelScope.launch(mainDispatcher) {
            changeStorageLocation.invoke(tree)
        }
    }

    private fun resetStorageLocation() {
        viewModelScope.launch(mainDispatcher) {
            revertToMediaStoreLocation()
        }
    }

    private fun refreshStorageSpace() {
        val location = state.storageLocation.takeUnless { state.isSecureSession } ?: return

        viewModelScope.launch(mainDispatcher) {
            val storageSpace = storageSpaceRepository.storageSpace(location)

            update {
                it.copy(
                    storageSpace = storageSpace,
                    isStorageSpaceLoaded = true,
                )
            }
        }
    }

    private fun videoModeQuality(lensFacing: LensFacing): VideoQuality {
        val slot = ModeSlot(
            mode = CameraMode.VIDEO,
            isFrontFacing = lensFacing == LensFacing.FRONT,
        )

        return settingsRepository.modeSettings(slot).videoQuality
    }

    private fun showDialog(dialog: Dialog?) {
        update { it.copy(dialog = dialog) }
    }

    private fun updateSettings(transform: (CameraSettings) -> CameraSettings) {
        settingsRepository.update(transform)
    }

    private fun update(transform: (CameraSettingsState) -> CameraSettingsState) {
        state = transform(state)
        _uiState.value = uiStateMapper.map(state)
    }
}
