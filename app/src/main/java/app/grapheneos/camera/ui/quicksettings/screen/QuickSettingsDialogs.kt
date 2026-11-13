package app.grapheneos.camera.ui.quicksettings.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.ui.quicksettings.components.OptionUnavailableDialog
import app.grapheneos.camera.ui.quicksettings.screen.model.OptionLimit
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsDialog as Dialog

@Composable
internal fun QuickSettingsDialogs(
    dialog: Dialog?,
    onAction: (Action) -> Unit,
) {
    when (dialog) {
        is Dialog.OptionUnavailable -> OptionUnavailableDialog(
            title = stringResource(titleOf(dialog.limit)),
            reason = stringResource(reasonOf(dialog.limit)),
            onDismiss = { onAction(Action.LimitDismissed) },
        )

        null -> Unit
    }
}

@StringRes
private fun titleOf(limit: OptionLimit): Int {
    return when (limit) {
        is OptionLimit.HighFrameRate -> R.string.quick_settings_frame_rate_unavailable
        is OptionLimit.LowLightBoostAtHighFrameRate -> R.string.quick_settings_low_light_unavailable
    }
}

@StringRes
private fun reasonOf(limit: OptionLimit): Int {
    return when (limit) {
        is OptionLimit.HighFrameRate -> when (limit.limit) {
            HighFrameRateLimit.HDR -> R.string.quick_settings_frame_rate_not_with_hdr
            HighFrameRateLimit.RESOLUTION -> R.string.quick_settings_frame_rate_not_at_resolution
        }

        is OptionLimit.LowLightBoostAtHighFrameRate -> {
            R.string.quick_settings_low_light_not_at_high_frame_rate
        }
    }
}
