package app.grapheneos.camera.ui.camerasettings.licenses.model

internal sealed interface LicensesScreenEffect {

    data class OpenUrl(
        val url: String,
    ) : LicensesScreenEffect
}
