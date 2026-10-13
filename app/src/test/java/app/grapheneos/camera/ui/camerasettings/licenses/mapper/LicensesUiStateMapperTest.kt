package app.grapheneos.camera.ui.camerasettings.licenses.mapper

import app.grapheneos.camera.data.licenses.model.OpenSourceLibrary
import app.grapheneos.camera.data.licenses.model.OpenSourceLicenses
import app.grapheneos.camera.ui.camerasettings.licenses.model.LibraryUiModel
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicenseUiModel
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LicensesUiStateMapperTest {

    private val mapper = LicensesUiStateMapperImpl()

    private val licenses = OpenSourceLicenses(
        libraries = listOf(
            OpenSourceLibrary(
                name = "Camera Core",
                coordinates = "androidx.camera:camera-core:1.6.2",
                license = "Apache-2.0",
                url = "https://developer.android.com",
            ),
            OpenSourceLibrary(
                name = "Jakarta Dependency Injection",
                coordinates = "jakarta.inject:jakarta.inject-api:2.0.1",
                license = "jakarta.inject:jakarta.inject-api",
            ),
        ),
        licenses = mapOf(
            "Apache-2.0" to "Apache License",
            "jakarta.inject:jakarta.inject-api" to "Apache License\n\nNotices for Jakarta",
        ),
    )

    @Test
    fun libraries_areListedInTheirOrder() {
        val uiState = mapper.map(LicensesState(licenses = licenses))

        assertEquals(
            listOf(
                LibraryUiModel(
                    name = "Camera Core",
                    coordinates = "androidx.camera:camera-core:1.6.2",
                ),
                LibraryUiModel(
                    name = "Jakarta Dependency Injection",
                    coordinates = "jakarta.inject:jakarta.inject-api:2.0.1",
                ),
            ),
            uiState.libraries,
        )
        assertNull(uiState.openLicense)
    }

    @Test
    fun openLibrary_showsItsLicenseText() {
        val uiState = mapper.map(
            LicensesState(
                licenses = licenses,
                openCoordinates = "jakarta.inject:jakarta.inject-api:2.0.1",
            ),
        )

        assertEquals(
            LicenseUiModel(
                name = "Jakarta Dependency Injection",
                coordinates = "jakarta.inject:jakarta.inject-api:2.0.1",
                text = "Apache License\n\nNotices for Jakarta",
            ),
            uiState.openLicense,
        )
    }
}
