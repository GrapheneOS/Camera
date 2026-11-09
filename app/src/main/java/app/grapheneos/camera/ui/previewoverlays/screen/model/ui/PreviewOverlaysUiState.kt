package app.grapheneos.camera.ui.previewoverlays.screen.model.ui

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import kotlin.time.Duration

@Immutable
internal data class PreviewOverlaysUiState(
    val focus: FocusUiModel? = null,
    val zoom: ZoomUiModel? = null,
    val gridPattern: CompositionGridPattern? = null,
    val selfTimerRemaining: Duration? = null,
)
