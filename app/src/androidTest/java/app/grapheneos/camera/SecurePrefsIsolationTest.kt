package app.grapheneos.camera

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.commonPreferences
import app.grapheneos.camera.data.core.store.modePreferences
import app.grapheneos.camera.data.settings.repository.SettingsKeys
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts the isolation through the repository the activities actually got. A binding that handed a
 * secure session the persistent preferences — or handed it a fresh copy on every lookup, so its own
 * changes were silently dropped — would satisfy every other test in this suite.
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

    private fun persistentCommons(): SharedPreferences {
        return commonPreferences(context, ephemeral = false)
    }

    private fun persistentModePrefs(): SharedPreferences {
        return modePreferences(context, ephemeral = false).getValue(MODE).value
    }

    private var ownersPhotoQuality: Int? = null
    private var ownersGeoTagging: Boolean? = null

    @Before
    fun rememberOwnersSettings() {
        ownersPhotoQuality = persistentCommons()
            .takeIf { it.contains(SettingsKeys.PHOTO_QUALITY) }
            ?.getInt(SettingsKeys.PHOTO_QUALITY, -1)
        ownersGeoTagging = persistentModePrefs()
            .takeIf { it.contains(SettingsKeys.GEO_TAGGING) }
            ?.getBoolean(SettingsKeys.GEO_TAGGING, false)
    }

    /** These are real settings, so the device is left configured the way it was found. */
    @After
    fun restoreOwnersSettings() {
        persistentCommons().edit().apply {
            when (val quality = ownersPhotoQuality) {
                null -> remove(SettingsKeys.PHOTO_QUALITY)
                else -> putInt(SettingsKeys.PHOTO_QUALITY, quality)
            }
        }.commit()

        persistentModePrefs().edit().apply {
            when (val geoTagging = ownersGeoTagging) {
                null -> remove(SettingsKeys.GEO_TAGGING)
                else -> putBoolean(SettingsKeys.GEO_TAGGING, geoTagging)
            }
        }.commit()
    }

    @Test
    fun writesInASecureSessionDoNotChangeThePersistentPrefs() {
        persistentCommons().edit().putInt(SettingsKeys.PHOTO_QUALITY, OWNERS_QUALITY).commit()

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    activity.settingsRepository.setPhotoQuality(SESSIONS_QUALITY).collect()
                }
            }
        }

        assertEquals(
            "A secure session wrote through to the persistent preferences",
            OWNERS_QUALITY,
            persistentCommons().getInt(SettingsKeys.PHOTO_QUALITY, -1),
        )
    }

    @Test
    fun aSecureSessionStillReadsTheOwnersSettings() {
        persistentCommons().edit().putInt(SettingsKeys.PHOTO_QUALITY, OWNERS_QUALITY).commit()

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    "The isolation must be one-way: a lockscreen session still honours the" +
                        " settings the owner chose",
                    OWNERS_QUALITY,
                    activity.settingsRepository.settings.value.photoQuality,
                )
            }
        }
    }

    /** A session handed a fresh copy on every lookup would read the owner's value back. */
    @Test
    fun aSecureSessionKeepsItsModeSettingsToItselfAndThenKeepsThem() {
        persistentModePrefs().edit().putBoolean(SettingsKeys.GEO_TAGGING, false).commit()

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val repository = activity.settingsRepository

                repository.reslotMode(mode = MODE, isFrontFacing = false)
                runBlocking { repository.setGeoTagging(true).collect() }
                repository.reslotMode(mode = MODE, isFrontFacing = false)

                assertTrue(
                    "The session lost its own mode-scoped write, so it was handed a second copy" +
                        " of the owner's preferences instead of the one it had been changing",
                    repository.modeSettings.value.geoTagging,
                )
            }
        }

        assertFalse(
            "A secure session wrote through to the persistent mode preferences",
            persistentModePrefs().getBoolean(SettingsKeys.GEO_TAGGING, false),
        )
    }

    @Test
    fun theRegularActivityDoesWriteThePersistentPrefs() {
        // The mirror of the tests above: if this ever fails, they would pass for the wrong
        // reason — because nothing writes preferences at all.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    activity.settingsRepository.setPhotoQuality(SESSIONS_QUALITY).collect()
                }
            }
        }

        assertEquals(
            SESSIONS_QUALITY,
            persistentCommons().getInt(SettingsKeys.PHOTO_QUALITY, -1),
        )
    }

    private companion object {
        val MODE = CameraMode.VIDEO

        const val OWNERS_QUALITY = 71
        const val SESSIONS_QUALITY = 42
    }
}
