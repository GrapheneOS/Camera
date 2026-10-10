package app.grapheneos.camera.ui.camerasettings.screen

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
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
    private val capturedItemRepository: CapturedItemRepository,
    private val changeStorageLocation: ChangeStorageLocation,
    private val revertToMediaStoreLocation: RevertToMediaStoreLocation,
    private val uiStateMapper: CameraSettingsUiStateMapper,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel(),
    CameraSettingsScreenModel {

    private var state = CameraSettingsState(
        settings = settingsRepository.settings.value,
        isSecureSession = entryPoint.isSecureSession,
        isCaptureSession = entryPoint.isCaptureSession,
        isZslSupported = isZslSupportedOf(arguments),
        photoResolutions = photoResolutionsOf(arguments),
    )

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
            }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.SwitchToggled -> onSwitchToggled(action)
            is Action.PhotoQualityClicked -> showDialog(Dialog.PhotoQuality)
            is Action.PhotoQualityConfirmed -> confirmPhotoQuality(action)
            is Action.CaptureModeClicked -> showDialog(Dialog.CaptureMode)
            is Action.CaptureModeConfirmed -> confirmCaptureMode(action.mode)
            is Action.StorageLocationClicked -> _effects.trySend(Effect.PickStorageLocation)
            is Action.StorageLocationPicked -> changeStorageLocation(action.tree)
            is Action.StorageLocationResetClicked -> resetStorageLocation()
            is Action.DialogDismissed -> showDialog(null)
        }
    }

    private fun onSwitchToggled(action: Action.SwitchToggled) {
        val enabled = action.enabled

        when (action) {
            is Action.RemoveExifToggled -> setRemoveExif(enabled)

            is Action.GyroscopeSuggestionsToggled -> {
                updateSettings { it.copy(gyroscopeSuggestions = enabled) }
            }

            is Action.KeepSelfiesAsSeenToggled -> {
                updateSettings { it.copy(keepSelfiesAsSeen = enabled) }
            }

            is Action.CameraSoundsToggled -> {
                updateSettings { it.copy(enableCameraSounds = enabled) }
            }
        }
    }

    private fun setRemoveExif(enabled: Boolean) {
        if (state.isCaptureSession) return

        updateSettings { it.copy(removeExifAfterCapture = enabled) }
    }

    private fun confirmPhotoQuality(action: Action.PhotoQualityConfirmed) {
        showDialog(null)

        updateSettings { settings ->
            settings.copy(
                photoQuality = action.quality
                    .takeIf { it in CameraSettings.PHOTO_QUALITY_RANGE }
                    ?: settings.photoQuality,
                selectHighestResolution = action.isFullResolution,
            )
        }
    }

    private fun confirmCaptureMode(mode: CaptureMode) {
        showDialog(null)

        updateSettings { it.copy(captureMode = mode) }
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
