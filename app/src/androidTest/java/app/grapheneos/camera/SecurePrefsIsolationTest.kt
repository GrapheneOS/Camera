package app.grapheneos.camera

import android.Manifest
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.di.preferences.DurableSettingsPrefsEntryPoint
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> block(activity.settingsRepository) }
        }
    }

    private val durableSettings: DataStore<SettingsPrefs> by lazy {
        EntryPointAccessors
            .fromApplication(context, DurableSettingsPrefsEntryPoint::class.java)
            .settingsPrefs()
    }

    private lateinit var ownersSettings: SettingsPrefs

    private fun stored(): SettingsPrefs {
        return runBlocking { durableSettings.data.first() }
    }

    @Before
    fun rememberOwnersSettings() {
        ownersSettings = stored()
    }

    @After
    fun restoreOwnersSettings() {
        runBlocking { durableSettings.updateData { ownersSettings } }
    }

    @Test
    fun writesInASecureSessionDoNotChangeThePersistentPrefs() {
        asTheOwner { repository ->
            runBlocking { repository.update { it.copy(photoQuality = OWNERS_QUALITY) } }
        }

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    activity.settingsRepository.update { it.copy(photoQuality = SESSIONS_QUALITY) }
                }
            }
        }

        assertEquals(
            "A secure session wrote through to the owner's preferences",
            OWNERS_QUALITY,
            stored().common.photoQuality,
        )
    }

    @Test
    fun aSecureSessionStillReadsTheOwnersSettings() {
        asTheOwner { repository ->
            runBlocking { repository.update { it.copy(photoQuality = OWNERS_QUALITY) } }
        }

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    "The isolation must be one-way: a lockscreen session still honours the" +
                        " settings the owner chose",
                    OWNERS_QUALITY,
                    runBlocking { activity.settingsRepository.settings.first() }.photoQuality,
                )
            }
        }
    }

    /** A session handed a fresh copy on every lookup would read the owner's value back. */
    @Test
    fun aSecureSessionKeepsItsModeSettingsToItselfAndThenKeepsThem() {
        asTheOwner { repository ->
            runBlocking {
                repository.selectMode(mode = MODE, isFrontFacing = false)
                repository.setGeoTagging(false)
            }
        }

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val repository = activity.settingsRepository

                val slotted = runBlocking {
                    repository.selectMode(mode = MODE, isFrontFacing = false)
                    repository.setGeoTagging(true)
                    repository.selectMode(mode = MODE, isFrontFacing = false)
                }

                assertTrue(
                    "The session lost its own mode-scoped write, so it was handed a second copy" +
                        " of the owner's preferences instead of the one it had been changing",
                    slotted.geoTagging,
                )
            }
        }

        assertEquals(
            "A secure session wrote through to the owner's mode preferences",
            false,
            stored().modes[MODE.name]?.geoTagging,
        )
    }

    @Test
    fun theRegularActivityDoesWriteThePersistentPrefs() {
        // The mirror of the tests above: if this ever fails, they would pass for the wrong
        // reason — because nothing writes preferences at all.
        asTheOwner { repository ->
            runBlocking { repository.update { it.copy(photoQuality = SESSIONS_QUALITY) } }
        }

        assertEquals(SESSIONS_QUALITY, stored().common.photoQuality)
    }

    private companion object {
        val MODE = CameraMode.VIDEO

        const val OWNERS_QUALITY = 71
        const val SESSIONS_QUALITY = 42
    }
}
