package app.grapheneos.camera.item

import android.hardware.camera2.CameraMetadata
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.grapheneos.camera.R

enum class WhiteBalance(
    val value: Int,
    @StringRes val uiName: Int,
    @DrawableRes val icon: Int
) {

    Auto(
        CameraMetadata.CONTROL_AWB_MODE_AUTO,
        R.string.white_balance_mode_auto,
        R.drawable.wb_auto
    ),
    CloudyDaylight(
        CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
        R.string.white_balance_mode_cloudy_daylight,
        R.drawable.wb_partly_cloudy_day
    ),
    DayLight(
        CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
        R.string.white_balance_mode_daylight,
        R.drawable.wb_sunny
    ),
    Fluorescent(
        CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT,
        R.string.white_balance_mode_fluorescent,
        R.drawable.wb_fluorescent
    ),
    Incandescent(
        CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT,
        R.string.white_balance_mode_incandescent,
        R.drawable.wb_incandescent
    ),
    Shade(
        CameraMetadata.CONTROL_AWB_MODE_SHADE,
        R.string.white_balance_mode_shade,
        R.drawable.wb_shade
    ),
    TwiLight(
        CameraMetadata.CONTROL_AWB_MODE_TWILIGHT,
        R.string.white_balance_mode_twiLight,
        R.drawable.wb_twilight
    ),
    WarmFluorescent(
        CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT,
        R.string.white_balance_mode_warm_fluorescent,
        R.drawable.wb_fluorescent
    )
}
