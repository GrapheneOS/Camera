package app.grapheneos.camera.ui.camerasettings.licenses.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class LicenseUiModel(
    val name: String,
    val coordinates: String,
    val text: String,
    val url: String? = null,
)
