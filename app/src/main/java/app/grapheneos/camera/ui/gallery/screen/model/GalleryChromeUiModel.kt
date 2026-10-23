package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class GalleryChromeUiModel(
    val showsBars: Boolean = true,
    val showsVideoControls: Boolean = true,
    val showsSystemBars: Boolean = true,
    val isImmersive: Boolean = false,
)
