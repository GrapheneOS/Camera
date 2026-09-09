package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.grapheneos.camera.R

data class SettingsSheetUiState(
    @DrawableRes val flashIcon: Int = R.drawable.flash_off_circle,
    @StringRes val flashDescription: Int = R.string.flash_off,
    val includeAudio: Boolean = false,
    val geoTagging: Boolean = false,
    val selfIllumination: Boolean = false,
    val includeAudioSettingVisible: Boolean = false,
    val videoQualitySettingVisible: Boolean = false,
    val stabilizationSettingVisible: Boolean = false,
    val selfIlluminationSettingVisible: Boolean = false,
    val timerSettingVisible: Boolean = true,
)
