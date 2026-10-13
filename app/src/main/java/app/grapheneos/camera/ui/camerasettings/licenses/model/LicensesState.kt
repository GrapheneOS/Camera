package app.grapheneos.camera.ui.camerasettings.licenses.model

import app.grapheneos.camera.data.licenses.model.OpenSourceLicenses

internal data class LicensesState(
    val licenses: OpenSourceLicenses = OpenSourceLicenses(),
    val openCoordinates: String? = null,
)
