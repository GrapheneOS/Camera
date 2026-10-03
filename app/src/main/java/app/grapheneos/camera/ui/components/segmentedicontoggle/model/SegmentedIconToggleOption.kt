package app.grapheneos.camera.ui.components.segmentedicontoggle.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
internal data class SegmentedIconToggleOption(
    val icon: ImageVector,
    val contentDescription: String,
)
