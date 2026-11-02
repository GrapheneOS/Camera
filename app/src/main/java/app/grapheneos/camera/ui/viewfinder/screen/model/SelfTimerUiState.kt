package app.grapheneos.camera.ui.viewfinder.screen.model

import kotlin.time.Duration

data class SelfTimerUiState(
    val remaining: Duration? = null,
    val badge: String = "",
    val badgeVisible: Boolean = false,
)
