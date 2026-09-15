package app.grapheneos.camera

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.di.preferences.DurableSettingsPrefsEntryPoint
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts the isolation through the repository the activities actually got. A binding that handed a
 * secure session the owner's store — or handed it a fresh copy on every lookup, so its own changes
 * were silently dropped — would satisfy every other test in this suite.
 */
@RunWith(AndroidJUnit4::class)
class SecurePrefsIsolationTest {
    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    /** Both activities bind a camera, which a dozing or locked device cannot provide. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val context: Context = InstrumentationRegistry
        .getInstrumentation()
        .targetContext
        .applicationContext

    private fun asTheOwner(block: (SettingsRepository) -> Unit) {
        inSession(MainActivity::class.java, block)
    }

    private fun asASecureSession(block: (SettingsRepository) -> Unit) {
        inSession(SecureMainActivity::class.java, block)
    }

    private fun <T : MainActivity> inSession(
        activityClass: Class<T>,
        block: (SettingsRepository) -> Unit,
    ) {
        ActivityScenario.launch(activityClass).use { scenario ->
            scenario.onActivity { activity ->
                block(activity.settingsRepository)
            }
        }
    }

    private val durable: DurableSettingsPrefsEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context, DurableSettingsPrefsEntryPoint::class.java)
    }

    private lateinit var ownersSettings: CameraSettings

    private var ownersGeoTagging = false

    private fun storedOnceItHolds(condition: (SettingsPrefs) -> Boolean): SettingsPrefs {
        return runBlocking {
            withTimeout(STORAGE_TIMEOUT) {
                durable.settingsPrefs().data.first(condition)
            }
        }
    }

    private fun storedAfterEveryEarlierWrite(): SettingsPrefs {
        durable.settingsRepository().update { it.copy(focusTimeoutSeconds = MARKER_FOCUS_TIMEOUT) }

        return storedOnceItHolds { it.common.focusTimeoutSeconds == MARKER_FOCUS_TIMEOUT }
    }

    @Before
    fun rememberOwnersSettings() {
        val owners = durable.settingsRepository()

        ownersSettings = owners.settings.value
        ownersGeoTagging = owners.modeSettings(SLOT).geoTagging
    }

    @After
    fun restoreOwnersSettings() {
        val owners = durable.settingsRepository()

        owners.update { ownersSettings }
        owners.setGeoTagging(SLOT, ownersGeoTagging)
    }

    @Test
    fun writesInASecureSessionDoNotChangeThePersistentPrefs() {
        asTheOwner { repository ->
            repository.update { it.copy(photoQuality = OWNERS_QUALITY) }
        }

        asASecureSession { repository ->
            repository.update { it.copy(photoQuality = SESSIONS_QUALITY) }
        }

        assertEquals(
            "A secure session wrote through to the owner's preferences",
            OWNERS_QUALITY,
            storedAfterEveryEarlierWrite().common.photoQuality,
        )
    }

    @Test
    fun aSecureSessionStillReadsTheOwnersSettings() {
        asTheOwner { repository ->
            repository.update { it.copy(photoQuality = OWNERS_QUALITY) }
        }

        asASecureSession { repository ->
            assertEquals(
                "The isolation must be one-way: a lockscreen session still honours the" +
                    " settings the owner chose",
                OWNERS_QUALITY,
                repository.settings.value.photoQuality,
            )
        }
    }

    /** A session handed a fresh copy on every lookup would read the owner's value back. */
    @Test
    fun aSecureSessionKeepsItsModeSettingsToItselfAndThenKeepsThem() {
        asTheOwner { repository ->
            repository.setGeoTagging(SLOT, false)
        }

        asASecureSession { repository ->
            repository.setGeoTagging(SLOT, true)

            assertTrue(
                "The session lost its own mode-scoped write, so it was handed a second copy" +
                    " of the owner's preferences instead of the one it had been changing",
                repository.modeSettings(SLOT).geoTagging,
            )
        }

        assertEquals(
            "A secure session wrote through to the owner's mode preferences",
            false,
            storedAfterEveryEarlierWrite().modes[MODE.name]?.geoTagging,
        )
    }

    @Test
    fun theRegularActivityDoesWriteThePersistentPrefs() {
        // The mirror of the tests above: if this ever fails, they would pass for the wrong
        // reason — because nothing writes preferences at all.
        asTheOwner { repository ->
            repository.update { it.copy(photoQuality = SESSIONS_QUALITY) }
        }

        storedOnceItHolds { it.common.photoQuality == SESSIONS_QUALITY }
    }

    private companion object {
        const val OWNERS_QUALITY = 71
        const val SESSIONS_QUALITY = 42
        const val MARKER_FOCUS_TIMEOUT = 97L

        val STORAGE_TIMEOUT = 5.seconds
        val MODE = CameraMode.VIDEO
        val SLOT = ModeSlot(mode = MODE, isFrontFacing = false)
    }
}
