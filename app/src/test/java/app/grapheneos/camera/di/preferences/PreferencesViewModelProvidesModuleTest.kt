package app.grapheneos.camera.di.preferences

import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesViewModelProvidesModuleTest {

    private val module = PreferencesViewModelProvidesModule()

    private val owners = mockk<SettingsRepository> {
        every { sessionCopy() } answers { mockk() }
    }

    @Test
    fun settingsRepository_inASecureSession_isTheCopyTheScreenIsGiven() {
        val secureSession = SecureSessionPreferences()
        val screens = secureSession.settingsRepository(owners)

        val viewModels = module.provideSettingsRepository(
            entryPoint = entryPoint(isSecureSession = true),
            owners = owners,
            secureSession = secureSession,
        )

        assertSame(screens, viewModels)
    }

    @Test
    fun settingsRepository_outsideASecureSession_isTheOwners() {
        val repository = module.provideSettingsRepository(
            entryPoint = entryPoint(isSecureSession = false),
            owners = owners,
            secureSession = SecureSessionPreferences(),
        )

        assertSame(owners, repository)
    }

    private fun entryPoint(isSecureSession: Boolean): CameraEntryPoint {
        return CameraEntryPoint(
            isSecureSession = isSecureSession,
            isCaptureSession = false,
            isVideoOnlySession = false,
            requiresVideoModeOnly = false,
            allowsQrScanning = true,
            showsCameraModeTabs = true,
        )
    }
}
