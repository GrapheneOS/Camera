package app.grapheneos.camera.ui.camerasettings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.camerasettings.components.GridIllustration
import app.grapheneos.camera.ui.camerasettings.components.PhotoQualityDialog
import app.grapheneos.camera.ui.camerasettings.components.PhotoQualityOption
import app.grapheneos.camera.ui.camerasettings.components.PhotoResolutionOptions
import app.grapheneos.camera.ui.camerasettings.components.SettingsChoice
import app.grapheneos.camera.ui.camerasettings.components.SettingsChoiceDialog
import app.grapheneos.camera.ui.camerasettings.components.SettingsConfirmDialog
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsDialog as Dialog
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeOption
import app.grapheneos.camera.ui.camerasettings.screen.model.ChoiceSettingUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.GridOption
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.VolumeKeyActionOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun CameraSettingsDialogs(
    uiState: UiState,
    onAction: (Action) -> Unit,
) {
    when (uiState.dialog) {
        is Dialog.Grid -> GridSettingDialog(
            grid = uiState.grid,
            onAction = onAction,
        )

        is Dialog.PhotoQuality -> PhotoQualitySettingDialog(
            photoQuality = uiState.photoQuality,
            onAction = onAction,
        )

        is Dialog.CaptureMode -> CaptureModeSettingDialog(
            captureMode = uiState.captureMode,
            onAction = onAction,
        )

        is Dialog.FocusTimeout -> FocusTimeoutSettingDialog(
            focusTimeout = uiState.focusTimeout,
            onAction = onAction,
        )

        is Dialog.VolumeKeyAction -> VolumeKeyActionSettingDialog(
            volumeKeyAction = uiState.volumeKeyAction,
            onAction = onAction,
        )

        is Dialog.ResetSettings -> ResetSettingsDialog(
            onAction = onAction,
        )

        null -> Unit
    }
}

@Composable
private fun GridSettingDialog(
    grid: ChoiceSettingUiModel<GridOption>,
    onAction: (Action) -> Unit,
) {
    SettingsChoiceDialog(
        title = stringResource(R.string.camera_settings_grid),
        choices = choices(
            options = grid.options,
            label = { stringResource(it.label) },
            trailing = { option -> { GridIllustration(pattern = option.pattern) } },
        ),
        selected = grid.selected,
        onConfirm = { onAction(Action.GridConfirmed(it.type)) },
        onDismiss = { onAction(Action.DialogDismissed) },
    )
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
    captureMode: ChoiceSettingUiModel<CaptureModeOption>,
    onAction: (Action) -> Unit,
) {
    SettingsChoiceDialog(
        title = stringResource(R.string.camera_settings_capture_mode),
        choices = choices(
            options = captureMode.options,
            label = { stringResource(it.label) },
            summary = { stringResource(it.summary) },
        ),
        selected = captureMode.selected,
        onConfirm = { onAction(Action.CaptureModeConfirmed(it.mode)) },
        onDismiss = { onAction(Action.DialogDismissed) },
    )
}

@Composable
private fun FocusTimeoutSettingDialog(
    focusTimeout: ChoiceSettingUiModel<Long>,
    onAction: (Action) -> Unit,
) {
    SettingsChoiceDialog(
        title = stringResource(R.string.camera_settings_focus_timeout),
        choices = choices(
            options = focusTimeout.options,
            label = { focusTimeoutLabel(it) },
        ),
        selected = focusTimeout.selected,
        onConfirm = { onAction(Action.FocusTimeoutConfirmed(it)) },
        onDismiss = { onAction(Action.DialogDismissed) },
    )
}

@Composable
private fun VolumeKeyActionSettingDialog(
    volumeKeyAction: ChoiceSettingUiModel<VolumeKeyActionOption>,
    onAction: (Action) -> Unit,
) {
    SettingsChoiceDialog(
        title = stringResource(R.string.camera_settings_volume_key_action),
        choices = choices(
            options = volumeKeyAction.options,
            label = { stringResource(it.label) },
        ),
        selected = volumeKeyAction.selected,
        onConfirm = { onAction(Action.VolumeKeyActionConfirmed(it.action)) },
        onDismiss = { onAction(Action.DialogDismissed) },
    )
}

@Composable
private fun ResetSettingsDialog(onAction: (Action) -> Unit) {
    SettingsConfirmDialog(
        title = stringResource(R.string.camera_settings_reset_settings_title),
        message = stringResource(R.string.camera_settings_reset_settings_message),
        confirmLabel = stringResource(R.string.camera_settings_reset),
        onConfirm = { onAction(Action.ResetSettingsConfirmed) },
        onDismiss = { onAction(Action.DialogDismissed) },
    )
}

@Composable
private fun <T> choices(
    options: ImmutableList<T>,
    label: @Composable (T) -> String,
    summary: @Composable (T) -> String? = { null },
    trailing: (T) -> (@Composable () -> Unit)? = { null },
): ImmutableList<SettingsChoice<T>> {
    return options
        .map { option ->
            SettingsChoice(
                value = option,
                label = label(option),
                summary = summary(option),
                trailing = trailing(option),
            )
        }
        .toImmutableList()
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

private fun photoResolutionOptions(resolution: PhotoResolutionUiModel): PhotoResolutionOptions {
    return PhotoResolutionOptions(
        standardMegapixels = resolution.standardMegapixels,
        fullMegapixels = resolution.fullMegapixels,
    )
}
