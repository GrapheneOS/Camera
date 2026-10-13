package app.grapheneos.camera.ui.camerasettings.licenses.model

internal sealed interface LicensesAction {

    data class LibraryClicked(
        val coordinates: String,
    ) : LicensesAction

    data object LicenseClosed : LicensesAction

    data class UrlClicked(
        val url: String,
    ) : LicensesAction
}
