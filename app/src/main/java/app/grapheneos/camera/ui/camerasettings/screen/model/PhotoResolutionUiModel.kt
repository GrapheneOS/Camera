package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class PhotoResolutionUiModel(
    val standardMegapixels: Int,
    val fullMegapixels: Int,
)
