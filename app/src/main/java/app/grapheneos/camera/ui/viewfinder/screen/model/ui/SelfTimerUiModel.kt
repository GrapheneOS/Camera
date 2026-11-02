package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import kotlin.time.Duration

data class SelfTimerUiModel(
    val remaining: Duration? = null,
    val badge: String = "",
    val badgeVisible: Boolean = false,
)
