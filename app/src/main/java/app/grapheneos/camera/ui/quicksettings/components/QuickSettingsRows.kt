package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined._30fpsSelect
import androidx.compose.material.icons.outlined._60fpsSelect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.ui.core.animation.crossfadeResizing
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSetting
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import app.grapheneos.camera.ui.settingvalues.flashIcon
import app.grapheneos.camera.ui.settingvalues.flashLabel
import app.grapheneos.camera.ui.settingvalues.selfTimerLabel
import app.grapheneos.camera.ui.settingvalues.videoQualityLabel
import app.grapheneos.camera.ui.settingvalues.videoQualityTitleId

@Composable
internal fun QuickSettingsRows(
    uiState: QuickSettingsUiState,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    LightRows(
        uiState = uiState,
        rowHighlight = rowHighlight,
        onAction = onAction,
    )
    PhotoRows(
        uiState = uiState,
        rowHighlight = rowHighlight,
        onAction = onAction,
    )
    VideoRows(
        uiState = uiState,
        rowHighlight = rowHighlight,
        onAction = onAction,
    )
}

@Composable
private fun LightRows(
    uiState: QuickSettingsUiState,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(setting = uiState.flash) { flash ->
        QuickSettingsRow(
            label = stringResource(R.string.flash_mode),
            setting = flash,
            onSelect = { mode -> onAction(Action.FlashModeSelected(mode)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { mode -> flashLabel(mode) },
            icon = { mode -> flashIcon(mode) },
            highlight = { rowHighlight.alphaOf(QuickSetting.FLASH) },
        )
    }
    AnimatedSettingRow(setting = uiState.selfIllumination) { selfIllumination ->
        QuickSettingsRow(
            label = stringResource(R.string.self_illumination),
            checked = selfIllumination,
            onToggle = { enabled -> onAction(Action.SelfIlluminationToggled(enabled)) },
            offIcon = Icons.Outlined.Block,
            onIcon = Icons.Outlined.Lightbulb,
            highlight = { rowHighlight.alphaOf(QuickSetting.SELF_ILLUMINATION) },
        )
    }
}

@Composable
private fun PhotoRows(
    uiState: QuickSettingsUiState,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(setting = uiState.selfTimer) { selfTimer ->
        QuickSettingsRow(
            label = stringResource(R.string.timer),
            setting = selfTimer,
            onSelect = { duration -> onAction(Action.SelfTimerSelected(duration)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { duration -> selfTimerLabel(duration) },
            highlight = { rowHighlight.alphaOf(QuickSetting.SELF_TIMER) },
        )
    }
    AnimatedSettingRow(setting = uiState.aspectRatio) { aspectRatio ->
        QuickSettingsRow(
            label = stringResource(R.string.aspect_ratio),
            setting = aspectRatio,
            onSelect = { ratio -> onAction(Action.AspectRatioSelected(ratio)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { ratio -> aspectRatioLabel(ratio) },
        )
    }
}

@Composable
private fun VideoRows(
    uiState: QuickSettingsUiState,
    rowHighlight: RowHighlightState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(setting = uiState.videoQuality) { videoQuality ->
        QuickSettingsRow(
            label = stringResource(R.string.quick_settings_resolution),
            setting = videoQuality,
            onSelect = { quality -> onAction(Action.VideoQualitySelected(quality)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { quality -> stringResource(videoQualityTitleId(quality)) },
            optionLabel = { quality -> videoQualityLabel(quality) },
            highlight = { rowHighlight.alphaOf(QuickSetting.VIDEO_QUALITY) },
        )
    }
    AnimatedSettingRow(setting = uiState.videoFrameRate) { videoFrameRate ->
        QuickSettingsRow(
            label = stringResource(R.string.quick_settings_frame_rate),
            setting = videoFrameRate,
            onSelect = { frameRate -> onAction(Action.VideoFrameRateSelected(frameRate)) },
            onLimitClick = { limit -> onAction(Action.LimitClicked(limit)) },
            valueLabel = { frameRate ->
                stringResource(R.string.quick_settings_fps, frameRate.framesPerSecond)
            },
            icon = { frameRate -> frameRateIcon(frameRate) },
        )
    }
    AnimatedSettingRow(setting = uiState.audio) { audio ->
        QuickSettingsRow(
            label = stringResource(R.string.include_audio),
            checked = audio,
            onToggle = { enabled -> onAction(Action.AudioToggled(enabled)) },
            offIcon = Icons.Outlined.MicOff,
            onIcon = Icons.Outlined.Mic,
        )
    }
}

@Composable
private fun <T> AnimatedSettingRow(
    setting: T?,
    row: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = setting,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = { crossfadeResizing() },
        contentKey = { shownSetting -> shownSetting != null },
    ) { shownSetting ->
        shownSetting?.let { row(it) }
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
