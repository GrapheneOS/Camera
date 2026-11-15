package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined._30fpsSelect
import androidx.compose.material.icons.outlined._60fpsSelect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.ui.core.animation.crossfadeResizing
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSetting
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction.RequiresIdleRecorder
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import app.grapheneos.camera.ui.settingvalues.flashIcon
import app.grapheneos.camera.ui.settingvalues.flashLabel
import app.grapheneos.camera.ui.settingvalues.selfTimerLabel
import app.grapheneos.camera.ui.settingvalues.videoQualityLabel
import app.grapheneos.camera.ui.settingvalues.videoQualityTitleId

@Composable
internal fun QuickSettingsRows(
    uiState: QuickSettingsUiState,
    enabled: Boolean,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    FlashRow(
        uiState = uiState,
        enabled = enabled,
        rowHighlight = rowHighlight,
        onAction = onAction,
    )
    PhotoRows(
        uiState = uiState,
        enabled = enabled,
        rowHighlight = rowHighlight,
        onAction = onAction,
    )
    VideoRows(
        uiState = uiState,
        enabled = enabled,
        rowHighlight = rowHighlight,
        onAction = onAction,
    )
}

@Composable
private fun FlashRow(
    uiState: QuickSettingsUiState,
    enabled: Boolean,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(
        setting = uiState.flash,
        enabled = enabled,
        layoutKey = { it.options },
    ) { flash, rowEnabled ->
        QuickSettingsRow(
            enabled = rowEnabled,
            label = stringResource(R.string.flash_mode),
            setting = flash,
            onSelect = { mode -> onAction(Action.FlashModeSelected(mode)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { mode -> flashLabel(mode) },
            icon = { mode -> flashIcon(mode) },
            highlight = { rowHighlight.alphaOf(QuickSetting.FLASH) },
        )
    }
}

@Composable
private fun PhotoRows(
    uiState: QuickSettingsUiState,
    enabled: Boolean,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(
        setting = uiState.selfTimer,
        enabled = enabled,
        layoutKey = { it.options },
    ) { selfTimer, rowEnabled ->
        QuickSettingsRow(
            enabled = rowEnabled,
            label = stringResource(R.string.timer),
            setting = selfTimer,
            onSelect = { duration -> onAction(RequiresIdleRecorder.SelfTimerSelected(duration)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { duration -> selfTimerLabel(duration) },
            highlight = { rowHighlight.alphaOf(QuickSetting.SELF_TIMER) },
        )
    }
    AnimatedSettingRow(
        setting = uiState.aspectRatio,
        enabled = enabled,
        layoutKey = { it.options },
    ) { aspectRatio, rowEnabled ->
        QuickSettingsRow(
            enabled = rowEnabled,
            label = stringResource(R.string.aspect_ratio),
            setting = aspectRatio,
            onSelect = { ratio -> onAction(RequiresIdleRecorder.AspectRatioSelected(ratio)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { ratio -> aspectRatioLabel(ratio) },
        )
    }
}

@Composable
private fun VideoRows(
    uiState: QuickSettingsUiState,
    enabled: Boolean,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(
        setting = uiState.videoQuality,
        enabled = enabled,
        layoutKey = { it.options },
    ) { videoQuality, rowEnabled ->
        QuickSettingsRow(
            enabled = rowEnabled,
            label = stringResource(R.string.quick_settings_resolution),
            setting = videoQuality,
            onSelect = { quality -> onAction(RequiresIdleRecorder.VideoQualitySelected(quality)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { quality -> stringResource(videoQualityTitleId(quality)) },
            optionLabel = { quality -> videoQualityLabel(quality) },
            highlight = { rowHighlight.alphaOf(QuickSetting.VIDEO_QUALITY) },
        )
    }
    AnimatedSettingRow(
        setting = uiState.videoFrameRate,
        enabled = enabled,
        layoutKey = { it.options },
    ) { videoFrameRate, rowEnabled ->
        QuickSettingsRow(
            enabled = rowEnabled,
            label = stringResource(R.string.quick_settings_frame_rate),
            setting = videoFrameRate,
            onSelect = { frameRate ->
                onAction(RequiresIdleRecorder.VideoFrameRateSelected(frameRate))
            },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { frameRate ->
                stringResource(R.string.quick_settings_fps, frameRate.framesPerSecond)
            },
            icon = { frameRate -> frameRateIcon(frameRate) },
        )
    }
    AnimatedSettingRow(
        setting = uiState.audio,
        enabled = enabled,
    ) { audio, rowEnabled ->
        QuickSettingsRow(
            enabled = rowEnabled,
            label = stringResource(R.string.include_audio),
            checked = audio,
            onToggle = { onAction(RequiresIdleRecorder.AudioToggled(enabled = it)) },
            offIcon = Icons.Outlined.MicOff,
            onIcon = Icons.Outlined.Mic,
        )
    }
}

@Composable
private fun <T> AnimatedSettingRow(
    setting: T?,
    enabled: Boolean,
    layoutKey: (T) -> Any = { Unit },
    row: @Composable (setting: T, enabled: Boolean) -> Unit,
) {
    val currentLayoutKey by rememberUpdatedState(setting?.let(layoutKey))

    AnimatedContent(
        targetState = setting,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = { crossfadeResizing() },
        contentKey = { shownSetting -> shownSetting?.let(layoutKey) },
    ) { shownSetting ->
        Box(modifier = Modifier.fillMaxWidth()) {
            if (shownSetting != null) {
                val isCurrentLayout = layoutKey(shownSetting) == currentLayoutKey
                row(shownSetting, enabled && isCurrentLayout)
            }
        }
    }
}

private fun frameRateIcon(frameRate: VideoFrameRate): ImageVector {
    return when (frameRate) {
        VideoFrameRate.FPS_30 -> Icons.Outlined._30fpsSelect
        VideoFrameRate.FPS_60 -> Icons.Outlined._60fpsSelect
    }
}

@Composable
private fun aspectRatioLabel(ratio: AspectRatio): String {
    return when (ratio) {
        AspectRatio.RATIO_4_3 -> stringResource(R.string.aspect_ratio_4_3)
        AspectRatio.RATIO_16_9 -> stringResource(R.string.aspect_ratio_16_9)
    }
}
