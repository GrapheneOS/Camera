package app.grapheneos.camera.ui.camerasettings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.camerasettings.components.PhotoQualityDialog
import app.grapheneos.camera.ui.camerasettings.components.PhotoQualityOption
import app.grapheneos.camera.ui.camerasettings.components.PhotoResolutionOptions
import app.grapheneos.camera.ui.camerasettings.components.SettingsChoice
import app.grapheneos.camera.ui.camerasettings.components.SettingsChoiceDialog
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsDialog as Dialog
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeOption
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun CameraSettingsDialogs(
    uiState: UiState,
    onAction: (Action) -> Unit,
) {
    when (uiState.dialog) {
        is Dialog.PhotoQuality -> PhotoQualitySettingDialog(
            photoQuality = uiState.photoQuality,
            onAction = onAction,
        )

        is Dialog.CaptureMode -> CaptureModeSettingDialog(
            captureMode = uiState.captureMode,
            onAction = onAction,
        )

        null -> Unit
    }
}

@Composable
private fun PhotoQualitySettingDialog(
    photoQuality: PhotoQualityUiModel,
    onAction: (Action) -> Unit,
) {
    PhotoQualityDialog(
        quality = photoQuality.quality,
        isFullResolution = photoQuality.isFullResolution,
        presets = photoQualityOptions(photoQuality.presets),
        custom = photoQualityOption(PhotoQualityLevel.CUSTOM),
        customRange = CameraSettings.PHOTO_QUALITY_RANGE,
        resolutions = photoQuality.resolution?.let { photoResolutionOptions(it) },
        onConfirm = { quality, isFullResolution ->
            onAction(
                Action.PhotoQualityConfirmed(
                    quality = quality,
                    isFullResolution = isFullResolution,
                ),
            )
        },
        onDismiss = { onAction(Action.DialogDismissed) },
    )
}

@Composable
private fun CaptureModeSettingDialog(
    captureMode: CaptureModeUiModel,
    onAction: (Action) -> Unit,
) {
    SettingsChoiceDialog(
        title = stringResource(R.string.camera_settings_capture_mode),
        choices = captureModeChoices(captureMode.options),
        selected = captureMode.selected.mode,
        onConfirm = { onAction(Action.CaptureModeConfirmed(it)) },
        onDismiss = { onAction(Action.DialogDismissed) },
    )
}

@Composable
private fun photoQualityOptions(
    levels: ImmutableList<PhotoQualityLevel>,
): ImmutableList<PhotoQualityOption> {
    return levels
        .map { photoQualityOption(it) }
        .toImmutableList()
}

@Composable
private fun photoQualityOption(level: PhotoQualityLevel): PhotoQualityOption {
    return PhotoQualityOption(
        quality = level.quality,
        label = stringResource(level.label),
    )
}

@Composable
private fun photoResolutionOptions(resolution: PhotoResolutionUiModel): PhotoResolutionOptions {
    return PhotoResolutionOptions(
        standard = stringResource(
            R.string.camera_settings_megapixels,
            resolution.standardMegapixels,
        ),
        full = stringResource(
            R.string.camera_settings_megapixels,
            resolution.fullMegapixels,
        ),
    )
}

@Composable
private fun captureModeChoices(
    options: ImmutableList<CaptureModeOption>,
): ImmutableList<SettingsChoice<CaptureMode>> {
    return options
        .map { option ->
            SettingsChoice(
                value = option.mode,
                label = stringResource(option.label),
                summary = stringResource(option.summary),
            )
        }
        .toImmutableList()
}
