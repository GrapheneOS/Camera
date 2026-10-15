package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.camera.model.LensFacing

@Immutable
internal data class SwitchSettingUiModel(
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val isChanged: Boolean = false,
    val limitedToLens: LensFacing? = null,
)
