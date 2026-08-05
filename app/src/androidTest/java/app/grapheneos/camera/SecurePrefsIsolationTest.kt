package app.grapheneos.camera

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A lockscreen session may read the owner's settings but must never write them: whoever picks
 * up a locked phone would otherwise be able to change what the owner sees after unlocking.
 * SecureMainActivity enforces this by overriding getSharedPreferences() to return an ephemeral
 * clone, and CamConfig obtains its preferences through the activity — rather than through the
 * application context — precisely so it inherits that.
 *
 * A settings repository injected with the application context would satisfy every other test
 * in this suite and silently undo it.
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

    private fun persistentPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @After
    fun removeProbeKey() {
        persistentPrefs().edit().remove(PROBE_KEY).commit()
    }

    @Test
    fun theSecureActivityDoesNotHandOutThePersistentPrefs() {
        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotSame(
                    "SecureMainActivity handed out the persistent preferences — a locked" +
                        " session can now overwrite the owner's settings",
                    persistentPrefs(),
                    activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                )
            }
        }
    }

    @Test
    fun writesInASecureSessionDoNotChangeThePersistentPrefs() {
        persistentPrefs().edit().putInt(PROBE_KEY, 1).commit()

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(PROBE_KEY, 2)
                    .commit()
            }
        }

        assertEquals(
            "A secure session wrote through to the persistent preferences",
            1,
            persistentPrefs().getInt(PROBE_KEY, -1),
        )
    }

    @Test
    fun aSecureSessionStillReadsTheOwnersSettings() {
        persistentPrefs().edit().putInt(PROBE_KEY, 3).commit()

        ActivityScenario.launch(SecureMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    "The isolation must be one-way: a lockscreen session still honours the" +
                        " settings the owner chose",
                    3,
                    activity
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(PROBE_KEY, -1),
                )
            }
        }
    }

    @Test
    fun theRegularActivityDoesWriteThePersistentPrefs() {
        // The mirror of the tests above: if this ever fails, they would pass for the wrong
        // reason — because nothing writes preferences at all.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(PROBE_KEY, 7)
                    .commit()
            }
        }

        assertEquals(7, persistentPrefs().getInt(PROBE_KEY, -1))
    }

    private companion object {
        // CamConfig.COMMON_SHARED_PREFS_NAME
        const val PREFS_NAME = "commons"

        /** Not a real setting, so a failed run cannot corrupt the app's configuration. */
        const val PROBE_KEY = "securePrefsIsolationProbe"
    }
}
