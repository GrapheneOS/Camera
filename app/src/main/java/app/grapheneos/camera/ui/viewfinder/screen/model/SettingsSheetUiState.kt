package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.camera.video.Quality
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.SettingsDefaults

data class SettingsSheetUiState(
    @DrawableRes val flashIcon: Int = R.drawable.flash_off_circle,
    @StringRes val flashDescription: Int = R.string.flash_off,
    val includeAudio: Boolean = false,
    val focusTimeoutSeconds: Long = SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
    val selfTimerSeconds: Int = 0,
    val videoQuality: Quality = SettingsDefaults.VIDEO_QUALITY,
    val geoTagging: Boolean = false,
    val selfIllumination: Boolean = false,
    val stabilizationEnabled: Boolean = false,
    val waitForFocusLock: Boolean = false,
    val is16by9: Boolean = false,
    val aspectRatioFixed: Boolean = false,
    @StringRes val aspectRatioDescription: Int = R.string.aspect_ratio_4_3,
    @DrawableRes val gridIcon: Int = R.drawable.grid_off_circle,
    @StringRes val gridDescription: Int = R.string.grid_off,
    val includeAudioSettingVisible: Boolean = false,
    val videoQualitySettingVisible: Boolean = false,
    val stabilizationSettingVisible: Boolean = false,
    val selfIlluminationSettingVisible: Boolean = false,
    val timerSettingVisible: Boolean = true,
    val waitForFocusLockSettingVisible: Boolean = true,
)
