package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.VolumeKeyAction

internal enum class VolumeKeyActionOption(
    val action: VolumeKeyAction,
    @StringRes val label: Int,
) {
    SHUTTER(
        action = VolumeKeyAction.SHUTTER,
        label = R.string.camera_settings_volume_key_action_shutter,
    ),
    ZOOM(
        action = VolumeKeyAction.ZOOM,
        label = R.string.camera_settings_volume_key_action_zoom,
    ),
    VOLUME(
        action = VolumeKeyAction.VOLUME,
        label = R.string.camera_settings_volume_key_action_volume,
    ),
    OFF(
        action = VolumeKeyAction.OFF,
        label = R.string.camera_settings_volume_key_action_off,
    ),
    ;

    companion object {
        fun of(action: VolumeKeyAction): VolumeKeyActionOption {
            return entries.first { it.action == action }
        }
    }
}
