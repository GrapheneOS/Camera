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
        secureSession.onSecureActivityDestroyed()

        assertSame(viewfinder, sessionRepository())
    }

    @Test
    fun theNextSession_getsACopyOfItsOwn() {
        secureSession.onSecureActivityCreated()

        val firstSession = sessionRepository()
        secureSession.onSecureActivityDestroyed()
        secureSession.onSecureActivityCreated()

        assertNotSame(firstSession, sessionRepository())
    }

    @Test
    fun anUnbalancedDestroy_doesNotStrandTheCounter() {
        secureSession.onSecureActivityDestroyed()
        secureSession.onSecureActivityCreated()

        val session = sessionRepository()
        secureSession.onSecureActivityDestroyed()
        secureSession.onSecureActivityCreated()

        assertNotSame(session, sessionRepository())
    }

    private fun sessionRepository(): SettingsRepository {
        return secureSession.settingsRepository(owners)
    }
}
