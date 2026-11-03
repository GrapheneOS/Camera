package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
data class SelfTimerUiModel(
    val remaining: Duration? = null,
    val badge: String = "",
    val badgeVisible: Boolean = false,
)
