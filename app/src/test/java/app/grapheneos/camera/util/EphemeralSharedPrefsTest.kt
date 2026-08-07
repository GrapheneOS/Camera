package app.grapheneos.camera.util

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [EphemeralSharedPrefs] is what stops a lockscreen session from changing the settings the
 * owner sees after unlocking: SecureMainActivity and SecureCaptureActivity override
 * getSharedPreferences() to hand out one of these, cloned from the real preferences but
 * backed by memory, and CamConfig deliberately reads its preferences through the activity so
 * it inherits that.
 *
 * The clone being one-way is the entire security property, and nothing asserted it.
 */
@RunWith(RobolectricTestRunner::class)
class EphemeralSharedPrefsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun persistentPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ephemeralPrefs(cloneOriginal: Boolean = true): SharedPreferences {
        return EphemeralSharedPrefsNamespace()
            .getPrefs(context, PREFS_NAME, Context.MODE_PRIVATE, cloneOriginal = cloneOriginal)
    }

    @Before
    fun resetPersistentPrefs() {
        persistentPrefs().edit().clear().commit()
    }

    @Test
    fun clonesExistingValuesFromThePersistentPrefs() {
        persistentPrefs().edit().putInt("photoQuality", 85).commit()

        assertEquals(85, ephemeralPrefs().getInt("photoQuality", -1))
    }

    @Test
    fun writesNeverReachThePersistentPrefs() {
        persistentPrefs().edit().putInt("photoQuality", 85).commit()

        val ephemeral = ephemeralPrefs()
        ephemeral.edit().putInt("photoQuality", 20).commit()

        assertEquals(20, ephemeral.getInt("photoQuality", -1))
        assertEquals(85, persistentPrefs().getInt("photoQuality", -1))
    }

    @Test
    fun removalsNeverReachThePersistentPrefs() {
        persistentPrefs().edit().putBoolean("includeAudio", true).commit()

        val ephemeral = ephemeralPrefs()
        ephemeral.edit().remove("includeAudio").commit()

        assertFalse(ephemeral.contains("includeAudio"))
        assertTrue(persistentPrefs().contains("includeAudio"))
    }

    @Test
    fun clearNeverReachesThePersistentPrefs() {
        persistentPrefs().edit().putBoolean("includeAudio", true).commit()

        val ephemeral = ephemeralPrefs()
        ephemeral.edit().clear().commit()

        assertFalse(ephemeral.contains("includeAudio"))
        assertTrue(persistentPrefs().contains("includeAudio"))
    }

    @Test
    fun aRepeatedLookupKeepsTheSessionsChanges() {
        persistentPrefs().edit().putInt("photoQuality", 85).commit()
        val namespace = EphemeralSharedPrefsNamespace()

        val first = namespace
            .getPrefs(context, PREFS_NAME, Context.MODE_PRIVATE, cloneOriginal = true)
        first.edit().putInt("photoQuality", 42).commit()
        val second = namespace
            .getPrefs(context, PREFS_NAME, Context.MODE_PRIVATE, cloneOriginal = true)

        // A second lookup that re-cloned from disk would silently discard everything the
        // session changed and hand back the persistent value instead.
        assertEquals(42, second.getInt("photoQuality", -1))
    }

    @Test
    fun startsEmptyWhenNotCloning() {
        persistentPrefs().edit().putInt("photoQuality", 85).commit()

        assertFalse(ephemeralPrefs(cloneOriginal = false).contains("photoQuality"))
    }

    @Test
    fun rejectsAnyModeOtherThanPrivate() {
        val failure = runCatching {
            EphemeralSharedPrefsNamespace()
                .getPrefs(context, PREFS_NAME, Context.MODE_APPEND, cloneOriginal = true)
        }.exceptionOrNull()

        assertTrue(
            "Only MODE_PRIVATE is supported, and anything else must fail loudly rather than" +
                " return preferences with the wrong semantics, but got $failure",
            failure is IllegalArgumentException,
        )
    }

    private companion object {
        // CamConfig.COMMON_SHARED_PREFS_NAME
        const val PREFS_NAME = "commons"
    }
}
