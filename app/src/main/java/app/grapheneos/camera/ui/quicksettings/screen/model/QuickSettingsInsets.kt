package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Distances from the screen's edges: to the preview's top and bottom, and to the shutter row. */
@Immutable
internal data class QuickSettingsInsets(
    val previewTop: Dp = 0.dp,
    val previewBottom: Dp = 0.dp,
    val shutterRow: Dp = 0.dp,
)
