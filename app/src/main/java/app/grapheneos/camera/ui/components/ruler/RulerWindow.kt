package app.grapheneos.camera.ui.components.ruler

import androidx.compose.runtime.Immutable

@Immutable
internal data class RulerWindow(
    val startInset: Float,
    val endInset: Float,
    val centerY: Float,
    val alignment: RulerTickAlignment,
)
