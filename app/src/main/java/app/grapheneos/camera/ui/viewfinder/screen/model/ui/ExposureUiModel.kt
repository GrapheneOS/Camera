package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.compose.runtime.Immutable

@Immutable
data class ExposureUiModel(
    val range: IntRange? = null,
    val progress: Int = 0,
    val panelVisible: Boolean = false,
)
