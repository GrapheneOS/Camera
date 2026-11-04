package app.grapheneos.camera.ui.previewoverlays.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import kotlin.time.Duration

@Immutable
internal data class PreviewOverlaysUiState(
    val gridPattern: CompositionGridPattern = CompositionGridPattern.Thirds,
    val isGridVisible: Boolean = false,
    val selfTimerRemaining: Duration? = null,
)
