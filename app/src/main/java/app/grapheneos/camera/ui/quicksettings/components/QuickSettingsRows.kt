package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
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
    onAction: (Action) -> Unit,
) {
    LightRows(
        uiState = uiState,
        onAction = onAction,
    )
    PhotoRows(
        uiState = uiState,
        onAction = onAction,
    )
    VideoRows(
        uiState = uiState,
        onAction = onAction,
    )
}

@Composable
private fun LightRows(
    uiState: QuickSettingsUiState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(setting = uiState.flash) { flash ->
        QuickSettingsRow(
            label = stringResource(R.string.flash_mode),
            setting = flash,
            onSelect = { mode -> onAction(Action.FlashModeSelected(mode)) },
            valueLabel = { mode -> flashLabel(mode) },
            icon = { mode -> flashIcon(mode) },
        )
    }
    AnimatedSettingRow(setting = uiState.videoLight) { videoLight ->
        QuickSettingsRow(
            label = stringResource(R.string.flash_mode),
            checked = videoLight,
            onToggle = { enabled -> onAction(Action.VideoLightToggled(enabled)) },
            offIcon = Icons.Outlined.FlashOff,
            onIcon = Icons.Outlined.FlashOn,
        )
    }
    AnimatedSettingRow(setting = uiState.selfIllumination) { selfIllumination ->
        QuickSettingsRow(
            label = stringResource(R.string.self_illumination),
            checked = selfIllumination,
            onToggle = { enabled -> onAction(Action.SelfIlluminationToggled(enabled)) },
            offIcon = Icons.Outlined.Block,
            onIcon = Icons.Outlined.Lightbulb,
        )
    }
}

@Composable
private fun PhotoRows(
    uiState: QuickSettingsUiState,
    onAction: (Action) -> Unit,
) {
    AnimatedSettingRow(setting = uiState.selfTimer) { selfTimer ->
        QuickSettingsRow(
            label = stringResource(R.string.timer),
            setting = selfTimer,
            onSelect = { duration -> onAction(Action.SelfTimerSelected(duration)) },
            valueLabel = { duration -> selfTimerLabel(duration) },
        )
    }
    AnimatedSettingRow(setting = uiState.aspectRatio) { aspectRatio ->
        QuickSettingsRow(
            label = stringResource(R.string.aspect_ratio),
            setting = aspectRatio,
            onSelect = { ratio -> onAction(Action.AspectRatioSelected(ratio)) },
            valueLabel = { ratio -> aspectRatioLabel(ratio) },
        )
    }
}

@Composable
private fun VideoRows(
    uiState: QuickSettingsUiState,
    onAction: (Action) -> Unit,
) {
    val onHighFrameRateLimitClick = { onAction(Action.HighFrameRateLimitClicked) }

    AnimatedSettingRow(setting = uiState.videoQuality) { videoQuality ->
        QuickSettingsRow(
            label = stringResource(R.string.quick_settings_resolution),
            setting = videoQuality,
            onSelect = { quality -> onAction(Action.VideoQualitySelected(quality)) },
            valueLabel = { quality -> stringResource(videoQualityTitleId(quality)) },
            optionLabel = { quality -> videoQualityLabel(quality) },
        )
    }
    AnimatedSettingRow(setting = uiState.videoFrameRate) { videoFrameRate ->
        QuickSettingsRow(
            label = stringResource(R.string.quick_settings_frame_rate),
            setting = videoFrameRate,
            onSelect = { frameRate -> onAction(Action.VideoFrameRateSelected(frameRate)) },
            valueLabel = { frameRate ->
                stringResource(R.string.quick_settings_fps, frameRate.framesPerSecond)
            },
            icon = { frameRate -> frameRateIcon(frameRate) },
            onUnavailableClick = onHighFrameRateLimitClick.takeIf {
                uiState.highFrameRateLimit != null
            },
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
