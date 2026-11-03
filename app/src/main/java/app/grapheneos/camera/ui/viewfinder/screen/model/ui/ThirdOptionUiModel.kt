package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.grapheneos.camera.R

@Immutable
data class ThirdOptionUiModel(
    val visible: Boolean = true,
    @DrawableRes val icon: Int = R.drawable.option_circle,
    @StringRes val description: Int = R.string.open_gallery,
    val thumbnail: Bitmap? = null,
    val thumbnailLoaderVisible: Boolean = false,
)
