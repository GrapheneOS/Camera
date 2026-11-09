package app.grapheneos.camera.ui.previewoverlays.screen.model.ui

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance

@Immutable
internal data class FocusUiModel(
    val requestId: Long = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val appearance: FocusIndicatorAppearance = FocusIndicatorAppearance.Focusing,
    val visible: Boolean = false,
)
