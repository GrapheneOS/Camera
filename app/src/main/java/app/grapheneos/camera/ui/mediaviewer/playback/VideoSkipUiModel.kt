package app.grapheneos.camera.ui.mediaviewer.playback

import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
internal data class VideoSkipUiModel(
    val direction: SkipDirection,
    val offset: Duration,
)
