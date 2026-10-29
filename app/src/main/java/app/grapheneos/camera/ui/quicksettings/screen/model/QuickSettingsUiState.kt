package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import kotlin.time.Duration

@Immutable
internal data class QuickSettingsUiState(
    val isVideoMode: Boolean = false,
    val isSelfIlluminating: Boolean = false,
    val flash: QuickChoiceUiModel<FlashMode>? = null,
    val torch: Boolean? = null,
    val selfIllumination: Boolean? = null,
    val selfTimer: QuickChoiceUiModel<Duration>? = null,
    val aspectRatio: QuickChoiceUiModel<AspectRatio>? = null,
    val videoQuality: QuickChoiceUiModel<VideoQuality>? = null,
    val audio: Boolean? = null,
)
