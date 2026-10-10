package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CaptureMode

internal enum class CaptureModeOption(
    val mode: CaptureMode,
    @StringRes val label: Int,
    @StringRes val summary: Int,
) {
    SPEED(
        mode = CaptureMode.SPEED,
        label = R.string.camera_settings_capture_mode_speed,
        summary = R.string.camera_settings_capture_mode_speed_summary,
    ),
    QUALITY(
        mode = CaptureMode.QUALITY,
        label = R.string.camera_settings_capture_mode_quality,
        summary = R.string.camera_settings_capture_mode_quality_summary,
    ),
    ZERO_SHUTTER_LAG(
        mode = CaptureMode.ZERO_SHUTTER_LAG,
        label = R.string.camera_settings_capture_mode_zero_shutter_lag,
        summary = R.string.camera_settings_capture_mode_zero_shutter_lag_summary,
    ),
    ;

    companion object {
        fun of(mode: CaptureMode): CaptureModeOption {
            return entries.first { it.mode == mode }
        }
    }
}
