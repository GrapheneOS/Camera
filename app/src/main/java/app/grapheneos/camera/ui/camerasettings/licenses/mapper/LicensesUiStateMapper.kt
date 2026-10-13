package app.grapheneos.camera.ui.camerasettings.licenses.mapper

import app.grapheneos.camera.ui.camerasettings.licenses.model.LibraryUiModel
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicenseUiModel
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesState
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesUiState
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList

internal interface LicensesUiStateMapper {
    fun map(state: LicensesState): LicensesUiState
}

internal class LicensesUiStateMapperImpl @Inject constructor() : LicensesUiStateMapper {

    override fun map(state: LicensesState): LicensesUiState {
        val licenses = state.licenses
        val openLibrary = licenses.libraries.find { it.coordinates == state.openCoordinates }

        return LicensesUiState(
            libraries = licenses.libraries
                .map {
                    LibraryUiModel(
                        name = it.name,
                        coordinates = it.coordinates,
                    )
                }
                .toImmutableList(),
            openLicense = openLibrary?.let { library ->
                LicenseUiModel(
                    name = library.name,
                    coordinates = library.coordinates,
                    text = licenses.licenses[library.license].orEmpty(),
                    url = library.url,
                )
            },
        )
    }
}
