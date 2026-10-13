package app.grapheneos.camera.ui.camerasettings.licenses.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class LicensesUiState(
    val libraries: ImmutableList<LibraryUiModel> = persistentListOf(),
    val openLicense: LicenseUiModel? = null,
)
