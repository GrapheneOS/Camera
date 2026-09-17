package app.grapheneos.camera.di.preferences

import app.grapheneos.camera.data.settings.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class SecureSessionPreferencesTest {

    private val secureSession = SecureSessionPreferences()
    private val owners = mockk<SettingsRepository>()

    @Before
    fun setUp() {
        every { owners.sessionCopy() } answers { mockk() }
    }

    @Test
    fun everyActivityOfOneSession_sharesOneCopy() {
        secureSession.onSecureActivityCreated()

        val viewfinder = sessionRepository()
        secureSession.onSecureActivityCreated()

        val settingsScreen = sessionRepository()
        assertSame(viewfinder, settingsScreen)
        verify(exactly = 1) { owners.sessionCopy() }
    }

    @Test
    fun aSessionThatStillHasAnActivity_keepsItsCopy() {
        secureSession.onSecureActivityCreated()

        val viewfinder = sessionRepository()
        secureSession.onSecureActivityCreated()
        // The settings screen of the same session closes; the viewfinder is still there.
        secureSession.onSecureActivityDestroyed(isChangingConfigurations = false)

        assertSame(viewfinder, sessionRepository())
    }

    @Test
    fun theNextSession_getsACopyOfItsOwn() {
        secureSession.onSecureActivityCreated()

        val firstSession = sessionRepository()
        secureSession.onSecureActivityDestroyed(isChangingConfigurations = false)
        secureSession.onSecureActivityCreated()

        assertNotSame(firstSession, sessionRepository())
    }

    @Test
    fun anUnbalancedDestroy_doesNotStrandTheCounter() {
        secureSession.onSecureActivityDestroyed(isChangingConfigurations = false)
        secureSession.onSecureActivityCreated()

        val session = sessionRepository()
        secureSession.onSecureActivityDestroyed(isChangingConfigurations = false)
        secureSession.onSecureActivityCreated()

        assertNotSame(session, sessionRepository())
    }

    @Test
    fun anActivityRecreatedForAConfigurationChange_keepsTheCopy() {
        secureSession.onSecureActivityCreated()

        val beforeRecreation = sessionRepository()
        secureSession.onSecureActivityDestroyed(isChangingConfigurations = true)
        secureSession.onSecureActivityCreated()

        assertSame(beforeRecreation, sessionRepository())
    }

    @Test
    fun theSessionAfterARecreatedOne_getsACopyOfItsOwn() {
        secureSession.onSecureActivityCreated()
        secureSession.onSecureActivityDestroyed(isChangingConfigurations = true)
        secureSession.onSecureActivityCreated()

        val recreatedSession = sessionRepository()
        secureSession.onSecureActivityDestroyed(isChangingConfigurations = false)
        secureSession.onSecureActivityCreated()

        assertNotSame(recreatedSession, sessionRepository())
    }

    private fun sessionRepository(): SettingsRepository {
        return secureSession.settingsRepository(owners)
    }
}
